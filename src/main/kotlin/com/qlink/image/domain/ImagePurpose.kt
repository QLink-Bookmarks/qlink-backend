package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import java.util.UUID

enum class ImagePurpose(
    private val keyKind: String?,
) {
    PROFILE("profile"),
    POST("post"),
    LINK_PREVIEW("preview"),
    FAVICON(null),
    ;

    fun newObjectKey(
        environment: String,
        type: ImageType,
    ): String {
        val kind = checkNotNull(keyKind) { "$name is not stored as a file upload" }
        val uniqueId = UUID.randomUUID().toString().replace("-", "")
        return "$environment-$kind-$uniqueId.${type.extension}"
    }

    companion object {
        fun from(value: String): ImagePurpose =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.IMAGE_PURPOSE_NOT_SUPPORTED)
    }
}
