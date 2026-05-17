package com.qlink.user.domain

import kotlin.time.Clock
import kotlin.time.Instant

data class User(
    val id: Long? = null,
    val displayName: String,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
