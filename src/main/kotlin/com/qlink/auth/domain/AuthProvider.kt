package com.qlink.auth.domain

import kotlin.time.Instant

data class AuthProvider(
    val id: Long,
    val userId: Long,
    val providerType: String,
    val providerId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
