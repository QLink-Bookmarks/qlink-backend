package com.qlink.ai.repository.table

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.link.repository.table.Links
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
    val userProviderId = reference("user_provider_id", UserProviders.id, onDelete = ReferenceOption.RESTRICT)
    val requestModelId = reference("request_model_id", AvailableModels.id, onDelete = ReferenceOption.RESTRICT)
    val responseModelId = reference("response_model_id", AvailableModels.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val requestedUrl = text("requested_url")
    val prompt = text("prompt")
    val response = text("response").nullable()
    val status = enumerationByName<AiJobStatus>("status", 1).default(AiJobStatus.P)
    val requestedAt = timestamp("requested_at").defaultExpression(CurrentTimestamp)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("ai_jobs_link_id_idx", false, linkId)
        index("ai_jobs_user_provider_id_idx", false, userProviderId)
        index("ai_jobs_request_model_id_idx", false, requestModelId)
        index("ai_jobs_response_model_id_idx", false, responseModelId)
        index("ai_jobs_status_idx", false, status)
    }
}

fun ResultRow.toAiJobDomain(): AiJob =
    AiJob(
        id = this[AiJobs.id],
        linkId = this[AiJobs.linkId],
        userProviderId = this[AiJobs.userProviderId],
        requestModelId = this[AiJobs.requestModelId],
        responseModelId = this[AiJobs.responseModelId],
        requestedUrl = this[AiJobs.requestedUrl],
        prompt = this[AiJobs.prompt],
        response = this[AiJobs.response],
        status = this[AiJobs.status],
        requestedAt = this[AiJobs.requestedAt].toKotlinInstant(),
        completedAt = this[AiJobs.completedAt]?.toKotlinInstant(),
        createdAt = this[AiJobs.createdAt].toKotlinInstant(),
        updatedAt = this[AiJobs.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(aiJob: AiJob) {
    this[AiJobs.linkId] = aiJob.linkId
    this[AiJobs.userProviderId] = aiJob.userProviderId
    this[AiJobs.requestModelId] = aiJob.requestModelId
    this[AiJobs.responseModelId] = aiJob.responseModelId
    this[AiJobs.requestedUrl] = aiJob.requestedUrl
    this[AiJobs.prompt] = aiJob.prompt
    this[AiJobs.response] = aiJob.response
    this[AiJobs.status] = aiJob.status
    aiJob.requestedAt?.let { this[AiJobs.requestedAt] = it.toJavaInstant() }
    this[AiJobs.completedAt] = aiJob.completedAt?.toJavaInstant()
}

fun UpdateBuilder<*>.refreshAiJobUpdatedAt() {
    this[AiJobs.updatedAt] = Clock.System.now().toJavaInstant()
}
