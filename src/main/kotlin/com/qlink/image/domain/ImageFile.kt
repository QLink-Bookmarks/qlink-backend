package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import java.util.UUID

class ImageFile private constructor(
    val bytes: ByteArray,
    val type: ImageType,
) {
    fun newObjectKey(): String {
        val uniqueId = UUID.randomUUID().toString().replace("-", "")
        return "$OBJECT_KEY_PREFIX$uniqueId.${type.extension}"
    }

    companion object {
        const val MAX_SIZE_BYTES: Int = 10 * 1024 * 1024
        const val OBJECT_KEY_PREFIX: String = "qlink_profile_"

        fun of(bytes: ByteArray): ImageFile {
            bytes.isNotEmpty().requireTrue(ErrorCode.IMAGE_FILE_REQUIRED)
            (bytes.size <= MAX_SIZE_BYTES).requireTrue(ErrorCode.IMAGE_FILE_TOO_LARGE)

            val type = ImageType.detect(bytes) ?: throw BusinessException(ErrorCode.IMAGE_INVALID_FORMAT)

            return ImageFile(bytes, type)
        }
    }
}
