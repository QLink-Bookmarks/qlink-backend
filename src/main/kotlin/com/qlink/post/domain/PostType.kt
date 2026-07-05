package com.qlink.post.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class PostType {
    ANNOUNCEMENT,
    FEEDBACK,
    ;

    fun validateManageable(isAdmin: Boolean) {
        if (this == ANNOUNCEMENT && !isAdmin) {
            throw BusinessException(ErrorCode.POST_ANNOUNCEMENT_FORBIDDEN)
        }
    }

    fun validateReadable(isAdmin: Boolean) {
        if (this == FEEDBACK && !isAdmin) {
            throw BusinessException(ErrorCode.POST_FEEDBACK_FORBIDDEN)
        }
    }

    val hasImage: Boolean
        get() = this == ANNOUNCEMENT

    companion object {
        fun from(value: String): PostType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.POST_TYPE_NOT_SUPPORTED)
    }
}
