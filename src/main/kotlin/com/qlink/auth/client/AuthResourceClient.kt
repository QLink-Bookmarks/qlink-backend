package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType

data class AuthResource(
    val providerType: AuthProviderType,
    val providerId: String,
)

interface AuthResourceClient {
    val providerType: AuthProviderType

    suspend fun getResource(token: String): AuthResource
}
