package com.qlink.auth.repository

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType

interface AuthProviderRepository {
    suspend fun findByProvider(
        providerType: AuthProviderType,
        providerId: String,
    ): AuthProvider?

    suspend fun insert(authProvider: AuthProvider): AuthProvider
}
