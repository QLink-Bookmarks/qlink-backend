package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.link.dto.CreateLinkPreviewResponse
import com.qlink.user.repository.UserRepository
import kotlinx.coroutines.CancellationException
import java.net.URI

class CreateLinkPreviewService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val previewClient: LinkPreviewSyncClient,
    private val urlResolver: PreviewImageUrlResolver,
) {
    suspend fun createPreview(
        loginId: Long,
        url: String,
    ): CreateLinkPreviewResponse {
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_PREVIEW_OWNER_NOT_FOUND)
        }
        if (!isAbsoluteHttpUrl(url)) throw BusinessException(ErrorCode.LINK_PREVIEW_URL_INVALID)

        val result =
            try {
                previewClient.extract(url = url, mode = LinkPreviewMode.PREVIEW)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        return createResponse(url = url, result = result)
    }

    private fun createResponse(
        url: String,
        result: LinkPreviewResult?,
    ): CreateLinkPreviewResponse {
        if (result == null) return CreateLinkPreviewResponse(url = url)

        val thumbnailUrl = result.thumbnailKey?.let(urlResolver::previewUrl)
        val faviconUrl =
            if (thumbnailUrl == null) {
                result.faviconKey?.let(urlResolver::faviconUrl)
            } else {
                null
            }

        return CreateLinkPreviewResponse(
            url = url,
            title =
                result.title
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(300),
            thumbnailUrl = thumbnailUrl,
            faviconUrl = faviconUrl,
            isVideo = result.isVideo,
        )
    }

    private fun isAbsoluteHttpUrl(value: String): Boolean =
        runCatching { URI(value) }.getOrNull()?.let {
            it.isAbsolute && it.host != null && it.userInfo == null &&
                (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true))
        } ?: false
}
