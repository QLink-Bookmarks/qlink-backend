package com.qlink.ai.repository.table

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.link.repository.table.Links
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object AiJobs : Table("ai_jobs") {
    val id = long("id").autoIncrement()
    val linkId = reference("link_id", Links.id, onDelete = ReferenceOption.CASCADE)
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val modelId = reference("model_id", AvailableModels.id, onDelete = ReferenceOption.RESTRICT)
    val status = enumerationByName<AiJobStatus>("status", 1).default(AiJobStatus.P)
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("ai_jobs_link_id_idx", false, linkId)
        index("ai_jobs_owner_id_idx", false, ownerId)
        index("ai_jobs_model_id_idx", false, modelId)
        index("ai_jobs_status_idx", false, status)
    }
}

fun ResultRow.toAiJobDomain(): AiJob =
    AiJob(
        id = this[AiJobs.id],
        linkId = this[AiJobs.linkId],
        ownerId = this[AiJobs.ownerId],
        modelId = this[AiJobs.modelId],
        status = this[AiJobs.status],
        errorMessage = this[AiJobs.errorMessage],
        createdAt = this[AiJobs.createdAt].toKotlinInstant(),
        updatedAt = this[AiJobs.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(aiJob: AiJob) {
    this[AiJobs.linkId] = aiJob.linkId
    this[AiJobs.ownerId] = aiJob.ownerId
    this[AiJobs.modelId] = aiJob.modelId
    this[AiJobs.status] = aiJob.status
    this[AiJobs.errorMessage] = aiJob.errorMessage
}

fun UpdateBuilder<*>.refreshAiJobUpdatedAt() {
    this[AiJobs.updatedAt] = Clock.System.now().toJavaInstant()
}
