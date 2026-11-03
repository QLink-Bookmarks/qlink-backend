package com.qlink.ai.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AiJob(
    val id: Long? = null,
    val linkId: Long,
    val userProviderId: Long,
    val requestModelId: Long,
    val responseModelId: Long? = null,
    val requestedUrl: String,
    val prompt: String,
    val response: String? = null,
    val status: AiJobStatus = AiJobStatus.P,
    val requestedAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val isPending: Boolean
        get() = status == AiJobStatus.P

    fun complete(
        responseModelId: Long,
        response: String,
        completedAt: Instant,
    ): AiJob =
        copy(
            responseModelId = responseModelId,
            response = response,
            status = AiJobStatus.C,
            completedAt = completedAt,
        )

    fun fail(completedAt: Instant): AiJob =
        copy(
            status = AiJobStatus.F,
            completedAt = completedAt,
        )

    fun recoveryAction(now: Instant): AiJobRecoveryAction =
        when {
            !isPending -> AiJobRecoveryAction.SKIP
            startedAt != null -> if (now - startedAt > STALE_AFTER) AiJobRecoveryAction.FAIL else AiJobRecoveryAction.SKIP
            requestedAt != null && now - requestedAt > STALE_AFTER -> AiJobRecoveryAction.FAIL
            else -> AiJobRecoveryAction.RUN
        }

    private fun copy(
        responseModelId: Long? = this.responseModelId,
        response: String? = this.response,
        status: AiJobStatus = this.status,
        completedAt: Instant? = this.completedAt,
    ): AiJob =
        AiJob(
            id = id,
            linkId = linkId,
            userProviderId = userProviderId,
            requestModelId = requestModelId,
            responseModelId = responseModelId,
            requestedUrl = requestedUrl,
            prompt = prompt,
            response = response,
            status = status,
            requestedAt = requestedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        val STALE_AFTER: Duration = 1.hours
    }
}
