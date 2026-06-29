package com.qlink.ai.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlin.time.Instant

class UserProvider(
    val id: Long? = null,
    val userId: Long,
    val providerId: Long,
    val userRole: Role = Role.NORMAL,
    val apiKey: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val isDefault: Boolean
        get() = userRole == Role.SUPER_ADMIN

    fun validateAccessibleBy(userId: Long) {
        if (this.userId != userId && !isDefault) {
            throw BusinessException(ErrorCode.AI_USER_PROVIDER_ACCESS_DENIED)
        }
    }
}
