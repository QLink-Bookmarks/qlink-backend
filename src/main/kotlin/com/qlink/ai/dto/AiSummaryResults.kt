package com.qlink.ai.dto

import com.qlink.ai.client.AiSummaryClientResponse

// Captured from the repository-returned link before the external AI request.
data class AiSummarySnapshot(
    val jobId: Long,
    val linkId: Long,
    val folderId: Long?,
)

data class AiSummaryCompletion(
    val snapshot: AiSummarySnapshot,
    val responseModelId: Long,
    val candidateFolderId: Long?,
    val response: AiSummaryClientResponse,
)

data class AiSummaryCompletionResult(
    val schedulingFailed: Boolean = false,
)
