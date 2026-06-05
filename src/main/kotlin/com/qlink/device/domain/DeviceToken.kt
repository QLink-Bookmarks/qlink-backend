package com.qlink.device.domain

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
}
