package com.qlink.ai.domain

import kotlin.time.Instant

class UserProvider(
    val id: Long? = null,
    val userId: Long,
    val providerId: Long,
    val role: UserProviderRole,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
