package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class DevicePlatform {
    WEB,
    NATIVE,
    ;

    companion object {
        fun fromName(name: String): DevicePlatform =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.DEVICE_PLATFORM_NOT_SUPPORTED)
    }
}
