package com.qlink.ai.domain

import kotlin.time.Instant

class AiJob(
    val id: Long? = null,
    val linkId: Long,
    val ownerId: Long,
    val modelId: Long,
    val status: AiJobStatus = AiJobStatus.P,
    val errorMessage: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
