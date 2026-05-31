package com.qlink.ai.repository

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.repository.table.AiProviders
import com.qlink.ai.repository.table.fromDomain
import com.qlink.ai.repository.table.refreshAiProviderUpdatedAt
import com.qlink.ai.repository.table.toAiProviderDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbAiProviderRepository : AiProviderRepository {
    override suspend fun insert(aiProvider: AiProvider): AiProvider =
        AiProviders
            .insertReturning { it.fromDomain(aiProvider) }
            .single()
            .toAiProviderDomain()

    override suspend fun findById(providerId: Long): AiProvider? =
        AiProviders
            .selectAll()
            .where { AiProviders.id eq providerId }
            .singleOrNull()
            ?.toAiProviderDomain()

    override suspend fun findByName(name: String): AiProvider? =
        AiProviders
            .selectAll()
            .where { AiProviders.name eq name }
            .singleOrNull()
            ?.toAiProviderDomain()

    override suspend fun update(aiProvider: AiProvider): AiProvider =
        AiProviders
            .updateReturning(where = { AiProviders.id eq aiProvider.id!! }) {
                it.fromDomain(aiProvider)
                it.refreshAiProviderUpdatedAt()
            }.single()
            .toAiProviderDomain()
}
