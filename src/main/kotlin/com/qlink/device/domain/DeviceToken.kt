package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import kotlin.time.Instant

class DeviceToken(
    val id: Long? = null,
    val userId: Long,
    val platform: DevicePlatform,
    val token: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        token.requireNotBlank(ErrorCode.DEVICE_TOKEN_BLANK)
    }

    fun validateOwner(ownerId: Long) {
        if (userId != ownerId) {
            throw BusinessException(ErrorCode.DEVICE_DIFFERENT_OWNER)
        }
    }

    fun changeOwner(ownerId: Long): DeviceToken =
        DeviceToken(
            id = id,
            userId = ownerId,
            platform = platform,
            token = token,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
