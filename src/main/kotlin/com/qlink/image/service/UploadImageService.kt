package com.qlink.image.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.common.transaction.TransactionRunner
import com.qlink.image.domain.ImageFile
import com.qlink.image.domain.ImagePurpose
import com.qlink.image.dto.UploadImageResponse
import com.qlink.image.storage.ImageStorage
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.user.repository.UserRepository
import kotlinx.coroutines.CancellationException
import java.net.URI

class UploadImageService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val imageStorage: ImageStorage,
    private val previewClient: LinkPreviewSyncClient,
    private val urlResolver: PreviewImageUrlResolver,
) {
    suspend fun upload(
        loginId: Long,
        purpose: String?,
        url: String?,
        bytes: ByteArray?,
    ): UploadImageResponse {
        tx.readOnly {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.IMAGE_OWNER_NOT_FOUND)
        }

        val imageUrl =
            when (val imagePurpose = purpose?.let(ImagePurpose::from) ?: ImagePurpose.PROFILE) {
                ImagePurpose.FAVICON -> collectFavicon(url = url, bytes = bytes)
                else -> uploadFile(purpose = imagePurpose, url = url, bytes = bytes)
            }

        return UploadImageResponse(url = imageUrl)
    }

    private suspend fun uploadFile(
        purpose: ImagePurpose,
        url: String?,
        bytes: ByteArray?,
    ): String {
        (url == null).requireTrue(ErrorCode.COMMON_BAD_REQUEST)

        val image = ImageFile.of(bytes ?: ByteArray(0))
        val key = purpose.newObjectKey(environment = urlResolver.environment, type = image.type)
        uploadFailingAs {
            imageStorage.upload(purpose = purpose, key = key, bytes = image.bytes, contentType = image.type.contentType)
        }

        return urlResolver.publicUrl(key)
    }

    private suspend fun collectFavicon(
        url: String?,
        bytes: ByteArray?,
    ): String {
        (bytes == null).requireTrue(ErrorCode.IMAGE_FILE_NOT_ALLOWED)
        val pageUrl = url?.takeIf(::isAbsoluteHttpUrl) ?: throw BusinessException(ErrorCode.IMAGE_URL_INVALID)

        return uploadFailingAs { previewClient.extract(url = pageUrl, mode = LinkPreviewMode.FAVICON_ONLY) }
            ?.faviconKey
            ?.let(urlResolver::faviconUrl)
            ?: throw BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED)
    }

    private suspend fun <T> uploadFailingAs(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, e)
        }

    private fun isAbsoluteHttpUrl(value: String): Boolean =
        runCatching { URI(value) }.getOrNull()?.let {
            it.isAbsolute && it.host != null && it.userInfo == null &&
                (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true))
        } ?: false
}
