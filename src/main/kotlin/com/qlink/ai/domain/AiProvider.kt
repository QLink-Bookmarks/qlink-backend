package com.qlink.ai.domain

import kotlin.time.Instant

class AiProvider(
    val id: Long? = null,
    val name: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
