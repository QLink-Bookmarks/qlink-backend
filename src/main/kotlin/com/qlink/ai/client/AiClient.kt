package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

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
    val linkId: Long?,
    val folderId: Long?,
    val rawResponse: String,
    val title: String,
    val summary: String,
    val tags: List<String>,
    val todos: List<AiSummaryTodo>,
    val usedTokens: Int,
)

data class AiSummaryTodo(
    val title: String,
    val reminderAt: Instant?,
)

interface AiClient {
    val providerType: AiProviderType

    suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse
}

private val summaryJson = Json { ignoreUnknownKeys = true }

internal fun parseSummaryResponse(
    rawResponse: String,
    usedTokens: Int,
): AiSummaryClientResponse {
    val normalizedResponse = rawResponse.extractJsonObject()
    val result = summaryJson.decodeFromString<AiSummaryResult>(normalizedResponse)

    return AiSummaryClientResponse(
        linkId = result.linkId ?: result.id,
        folderId = result.folderId,
        rawResponse = normalizedResponse,
        title = result.title,
        summary = result.summary,
        tags = result.tags,
        todos = result.todos.map { AiSummaryTodo(title = it.title, reminderAt = it.reminderAt?.let(Instant::parse)) },
        usedTokens = usedTokens,
    )
}

private fun String.extractJsonObject(): String {
    val text = trim().removeMarkdownJsonFence().trim()
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')

    return if (start in 0..end) {
        text.substring(start, end + 1)
    } else {
        text
    }
}

private fun String.removeMarkdownJsonFence(): String =
    removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")

@Serializable
private data class AiSummaryResult(
    val id: Long? = null,
    val linkId: Long? = null,
    val folderId: Long? = null,
    val title: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    val todos: List<AiSummaryTodoResult> = emptyList(),
)

@Serializable
private data class AiSummaryTodoResult(
    val title: String,
    val reminderAt: String? = null,
)
