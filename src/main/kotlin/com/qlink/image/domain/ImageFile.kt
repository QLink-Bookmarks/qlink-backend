package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue

class ImageFile private constructor(
    bytes: ByteArray,
    val type: ImageType,
) {
    private val content: ByteArray = bytes.copyOf()

    val bytes: ByteArray
        get() = content.copyOf()

    companion object {
        const val MAX_SIZE_BYTES: Int = 10 * 1024 * 1024

        fun of(bytes: ByteArray): ImageFile {
            bytes.isNotEmpty().requireTrue(ErrorCode.IMAGE_FILE_REQUIRED)
            (bytes.size <= MAX_SIZE_BYTES).requireTrue(ErrorCode.IMAGE_FILE_TOO_LARGE)

            val type = ImageType.detect(bytes) ?: throw BusinessException(ErrorCode.IMAGE_INVALID_FORMAT)

            return ImageFile(bytes = bytes, type = type)
        }
    }
}
