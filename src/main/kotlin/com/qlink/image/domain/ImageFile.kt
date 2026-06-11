package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import java.util.UUID

/**
 * A validated image ready to be stored. Construction guarantees the bytes are a
 * supported image format and within the size limit.
 */
class ImageFile private constructor(
    val bytes: ByteArray,
    val type: ImageType,
) {
    fun newObjectKey(): String = "images/${UUID.randomUUID()}.${type.extension}"

    companion object {
        const val MAX_SIZE_BYTES: Int = 10 * 1024 * 1024

        fun of(bytes: ByteArray): ImageFile {
            bytes.isNotEmpty().requireTrue(ErrorCode.IMAGE_FILE_REQUIRED)
            (bytes.size <= MAX_SIZE_BYTES).requireTrue(ErrorCode.IMAGE_FILE_TOO_LARGE)

            val type = ImageType.detect(bytes) ?: throw BusinessException(ErrorCode.IMAGE_INVALID_FORMAT)

            return ImageFile(bytes, type)
        }
    }
}
