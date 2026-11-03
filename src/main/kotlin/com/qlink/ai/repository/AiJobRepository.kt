package com.qlink.ai.repository

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import kotlin.time.Duration
import kotlin.time.Instant

interface AiJobRepository {
    suspend fun insert(aiJob: AiJob): AiJob

    suspend fun findById(aiJobId: Long): AiJob?

    suspend fun findAllByLinkId(linkId: Long): List<AiJob>

    suspend fun findAllByStatus(status: AiJobStatus): List<AiJob>

    suspend fun update(aiJob: AiJob): AiJob

    suspend fun markStarted(
        aiJobId: Long,
        staleAfter: Duration,
    ): Boolean

    suspend fun countNotStarted(): Long

    suspend fun now(): Instant
}
