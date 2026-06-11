package com.qlink.image.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.image.domain.ImageFile
import com.qlink.image.dto.UploadImageResponse
import com.qlink.image.storage.ImageStorage
import kotlinx.coroutines.CancellationException

class UploadImageService(
    private val imageStorage: ImageStorage,
) {
    suspend fun upload(bytes: ByteArray?): UploadImageResponse {
        val image = ImageFile.of(bytes ?: ByteArray(0))
        val key = image.newObjectKey()
        val url = storeOrFail(key, image)

        return UploadImageResponse(url = url)
    }

    private suspend fun storeOrFail(
        key: String,
        image: ImageFile,
    ): String =
        try {
            imageStorage.upload(key = key, bytes = image.bytes, contentType = image.type.contentType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, e)
        }
}
