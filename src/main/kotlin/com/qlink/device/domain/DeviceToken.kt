package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import kotlin.time.Instant

const val DEVICE_TOKEN_MAX_LENGTH = 4096

class DeviceToken(
    val id: Long? = null,
    val userId: Long,
    val platform: DevicePlatform,
    val token: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        if (token.isBlank()) {
            throw BusinessException(ErrorCode.DEVICE_TOKEN_BLANK)
        }
        token.requireNotOver(DEVICE_TOKEN_MAX_LENGTH, ErrorCode.DEVICE_TOKEN_OVER_MAX)
    }
}
