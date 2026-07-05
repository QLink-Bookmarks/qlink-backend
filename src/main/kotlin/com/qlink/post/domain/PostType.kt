package com.qlink.post.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class PostType {
    ANNOUNCEMENT,
    FEEDBACK,
    ;

    fun validateManageable(role: Role) {
        if (this == ANNOUNCEMENT && role !in ADMIN_ROLES) {
            throw BusinessException(ErrorCode.POST_ANNOUNCEMENT_FORBIDDEN)
        }
    }

    companion object {
        private val ADMIN_ROLES = setOf(Role.ADMIN, Role.SUPER_ADMIN)

        fun from(value: String): PostType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.POST_TYPE_NOT_SUPPORTED)
    }
}
