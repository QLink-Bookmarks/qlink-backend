package com.qlink.ai.repository

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType

interface AiProviderRepository {
    suspend fun insert(aiProvider: AiProvider): AiProvider

    suspend fun findById(providerId: Long): AiProvider?

    suspend fun findByType(type: AiProviderType): AiProvider?

    suspend fun update(aiProvider: AiProvider): AiProvider
}
