package com.qlink.ai.repository

import com.qlink.ai.domain.AiProvider

interface AiProviderRepository {
    suspend fun insert(aiProvider: AiProvider): AiProvider

    suspend fun findById(providerId: Long): AiProvider?

    suspend fun findByName(name: String): AiProvider?

    suspend fun update(aiProvider: AiProvider): AiProvider
}
