package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType

data class AiSummaryPrompt(
    val linkId: Long,
    val url: String,
)

data class AiSummaryClientRequest(
    val providerType: AiProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val prompt: String,
)

data class AiSummaryClientResponse(
    val rawResponse: String,
    val title: String,
    val summary: String,
    val todos: List<AiSummaryTodo>,
    val usedTokens: Int,
)

data class AiSummaryTodo(
    val title: String,
    val reminderAt: kotlin.time.Instant?,
)

interface AiClient {
    val providerType: AiProviderType

    suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse
}
