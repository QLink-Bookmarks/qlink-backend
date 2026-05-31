package com.qlink.ai.repository

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus

interface AiJobRepository {
    suspend fun insert(aiJob: AiJob): AiJob

    suspend fun findById(aiJobId: Long): AiJob?

    suspend fun findAllByLinkId(linkId: Long): List<AiJob>

    suspend fun findAllByStatus(status: AiJobStatus): List<AiJob>

    suspend fun update(aiJob: AiJob): AiJob
}
