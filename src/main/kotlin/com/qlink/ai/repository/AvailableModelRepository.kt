package com.qlink.ai.repository

import com.qlink.ai.domain.AvailableModel

interface AvailableModelRepository {
    suspend fun insert(availableModel: AvailableModel): AvailableModel

    suspend fun findById(modelId: Long): AvailableModel?

    suspend fun findAllByProviderId(providerId: Long): List<AvailableModel>

    suspend fun update(availableModel: AvailableModel): AvailableModel
}
