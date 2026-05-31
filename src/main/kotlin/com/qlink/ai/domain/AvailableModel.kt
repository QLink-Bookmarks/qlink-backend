package com.qlink.ai.domain

import kotlin.time.Instant

class AvailableModel(
    val id: Long? = null,
    val providerId: Long,
    val model: String,
    val priority: Int,
    val rpdLimit: Int?,
    val tpdLimit: Int?,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
