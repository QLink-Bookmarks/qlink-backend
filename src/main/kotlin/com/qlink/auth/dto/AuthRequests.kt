@file:Suppress("ktlint:standard:filename")

package com.qlink.auth.dto

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlinx.serialization.Serializable

enum class AuthPlatform {
    WEB,
    NATIVE,
}

@Serializable
data class SignInRequest(
    val provider: String,
    val token: String,
    val platform: AuthPlatform,
)

@Serializable
data class ConnectAuthProviderRequest(
    val provider: String,
    val token: String,
)

@Serializable
data class NativeRefreshTokenRequest(
    val refreshToken: String? = null,
) {
    fun requireRefreshToken(): String =
        refreshToken?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISSING)
}

@Serializable
data class SignOutRequest(
    val refreshToken: String? = null,
)
