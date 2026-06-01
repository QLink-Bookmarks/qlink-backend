package com.qlink.ai.domain

import com.qlink.auth.domain.Role
import kotlin.time.Instant

class UserProvider(
    val id: Long? = null,
    val userId: Long,
    val providerId: Long,
    val userRole: Role,
    val apiKey: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
