package com.qlink.user.domain

import kotlin.time.Instant

class User(
    val id: Long? = null,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
