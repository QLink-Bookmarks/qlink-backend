package com.qlink.ai.client

data class AiSummaryPrompt(
    val url: String,
    val title: String,
    val memo: String?,
    val tags: List<String>,
)

interface AiClient {
    val provider: AiProvider

    suspend fun summarize(prompt: AiSummaryPrompt): String
}
