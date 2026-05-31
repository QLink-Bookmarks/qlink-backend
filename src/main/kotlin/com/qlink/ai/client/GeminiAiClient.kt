package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GeminiAiClient(
    private val httpClient: HttpClient,
) : AiClient {
    override val providerType: AiProviderType = AiProviderType.GEMINI

    override suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse {
        val response =
            httpClient
                .post("${request.baseUrl}/models/${request.model}:generateContent") {
                    parameter("key", request.apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(GeminiGenerateContentRequest.from(request.prompt))
                }.body<GeminiGenerateContentResponse>()
        val text =
            response
                .candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstNotNullOfOrNull { it.text?.trim()?.takeIf(String::isNotBlank) }
                ?: throw BusinessException(ErrorCode.AI_EMPTY_RESPONSE)

        return parseSummaryResponse(rawResponse = text)
    }
}

@Serializable
private data class GeminiGenerateContentRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfig,
) {
    companion object {
        fun from(prompt: String): GeminiGenerateContentRequest =
            GeminiGenerateContentRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                tools = listOf(GeminiTool(urlContext = emptyMap())),
                generationConfig = GeminiGenerationConfig(),
            )
    }
}

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
)

@Serializable
private data class GeminiTool(
    @SerialName("url_context")
    val urlContext: Map<String, String>,
)

@Serializable
private data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
)

@Serializable
private data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun parseSummaryResponse(rawResponse: String): AiSummaryClientResponse {
    val result = json.decodeFromString<AiSummaryResult>(rawResponse)

    return AiSummaryClientResponse(
        rawResponse = rawResponse,
        title = result.title,
        summary = result.summary,
        todos = result.todos.map { AiSummaryTodo(title = it.title, reminderAt = it.reminderAt?.let(kotlin.time.Instant::parse)) },
        usedTokens = rawResponse.length,
    )
}

internal val systemInstruction: String =
    """
    - Always respond in Korean.
    - Always respond in the given JSON format.
    - If any safety issue may happen, don't block and just mention in the summary.
    - Use UTC timezone for reminderAt.
    - If there is no todo, return an empty todos array.
    """.trimIndent()

@Serializable
private data class AiSummaryResult(
    val id: Long,
    val title: String,
    val summary: String,
    val todos: List<AiSummaryTodoResult> = emptyList(),
)

@Serializable
private data class AiSummaryTodoResult(
    val title: String,
    val reminderAt: String? = null,
)
