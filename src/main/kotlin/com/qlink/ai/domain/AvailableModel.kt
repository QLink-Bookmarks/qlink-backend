package com.qlink.ai.domain

import kotlin.time.Instant

class AvailableModel(
    val id: Long? = null,
    val providerId: Long,
    val modelKey: String,
    val displayName: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
