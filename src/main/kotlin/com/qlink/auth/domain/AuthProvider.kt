package com.qlink.auth.domain

import kotlin.time.Instant

data class AuthProvider(
    val id: Long? = null,
    val userId: Long,
    val providerType: AuthProviderType,
    val providerId: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
