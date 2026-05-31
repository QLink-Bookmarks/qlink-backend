package com.qlink.ai.repository

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.repository.table.AiJobs
import com.qlink.ai.repository.table.fromDomain
import com.qlink.ai.repository.table.refreshAiJobUpdatedAt
import com.qlink.ai.repository.table.toAiJobDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbAiJobRepository : AiJobRepository {
    override suspend fun insert(aiJob: AiJob): AiJob =
        AiJobs
            .insertReturning { it.fromDomain(aiJob) }
            .single()
            .toAiJobDomain()

    override suspend fun findById(aiJobId: Long): AiJob? =
        AiJobs
            .selectAll()
            .where { AiJobs.id eq aiJobId }
            .singleOrNull()
            ?.toAiJobDomain()

    override suspend fun findAllByLinkId(linkId: Long): List<AiJob> =
        AiJobs
            .selectAll()
            .where { AiJobs.linkId eq linkId }
            .orderBy(AiJobs.id to SortOrder.ASC)
            .map { it.toAiJobDomain() }

    override suspend fun findAllByStatus(status: AiJobStatus): List<AiJob> =
        AiJobs
            .selectAll()
            .where { AiJobs.status eq status }
            .orderBy(AiJobs.id to SortOrder.ASC)
            .map { it.toAiJobDomain() }

    override suspend fun update(aiJob: AiJob): AiJob =
        AiJobs
            .updateReturning(where = { AiJobs.id eq aiJob.id!! }) {
                it.fromDomain(aiJob)
                it.refreshAiJobUpdatedAt()
            }.single()
            .toAiJobDomain()
}
