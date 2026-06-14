package com.qlink.auth.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class AuthProviderType {
    KAKAO,
    GOOGLE,
    NAVER,
    ;

    companion object {
        fun fromRequestName(requestName: String): AuthProviderType =
            entries.firstOrNull { it.name.equals(requestName, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED)
    }
}
