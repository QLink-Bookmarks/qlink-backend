package com.qlink.ai.repository

import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.table.AvailableModels
import com.qlink.ai.repository.table.fromDomain
import com.qlink.ai.repository.table.refreshAvailableModelUpdatedAt
import com.qlink.ai.repository.table.toAvailableModelDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbAvailableModelRepository : AvailableModelRepository {
    override suspend fun insert(availableModel: AvailableModel): AvailableModel =
        AvailableModels
            .insertReturning { it.fromDomain(availableModel) }
            .single()
            .toAvailableModelDomain()

    override suspend fun findById(modelId: Long): AvailableModel? =
        AvailableModels
            .selectAll()
            .where { AvailableModels.id eq modelId }
            .singleOrNull()
            ?.toAvailableModelDomain()

    override suspend fun findAllByProviderId(providerId: Long): List<AvailableModel> =
        AvailableModels
            .selectAll()
            .where { AvailableModels.providerId eq providerId }
            .orderBy(AvailableModels.id to SortOrder.ASC)
            .map { it.toAvailableModelDomain() }

    override suspend fun update(availableModel: AvailableModel): AvailableModel =
        AvailableModels
            .updateReturning(where = { AvailableModels.id eq availableModel.id!! }) {
                it.fromDomain(availableModel)
                it.refreshAvailableModelUpdatedAt()
            }.single()
            .toAvailableModelDomain()
}
