package com.qlink.ai.domain

import kotlin.time.Instant

class AiProvider(
    val id: Long? = null,
    val type: AiProviderType,
    val baseUrl: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
