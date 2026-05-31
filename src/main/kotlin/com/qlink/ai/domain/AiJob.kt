package com.qlink.ai.domain

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
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun complete(
        responseModelId: Long,
        response: String,
        completedAt: Instant,
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
            status = AiJobStatus.C,
            requestedAt = requestedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun fail(completedAt: Instant): AiJob =
        AiJob(
            id = id,
            linkId = linkId,
            userProviderId = userProviderId,
            requestModelId = requestModelId,
            responseModelId = responseModelId,
            requestedUrl = requestedUrl,
            prompt = prompt,
            response = response,
            status = AiJobStatus.F,
            requestedAt = requestedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
