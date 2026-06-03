package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class DevicePlatform(
    val requestName: String,
) {
    IOS("ios"),
    ANDROID("android"),
    ;

    companion object {
        fun fromRequestName(requestName: String): DevicePlatform =
            entries.firstOrNull { it.requestName == requestName }
                ?: throw BusinessException(ErrorCode.DEVICE_PLATFORM_NOT_SUPPORTED)
    }
}
