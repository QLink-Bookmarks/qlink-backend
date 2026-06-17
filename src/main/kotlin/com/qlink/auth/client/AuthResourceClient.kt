package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform

data class AuthResource(
    val providerType: AuthProviderType,
    val providerId: String,
)

interface AuthResourceClient {
    val providerType: AuthProviderType

    suspend fun getResource(
        token: String,
        platform: AuthPlatform,
    ): AuthResource
}
