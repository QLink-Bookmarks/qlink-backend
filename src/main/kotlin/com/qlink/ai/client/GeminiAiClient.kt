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
import kotlinx.serialization.json.JsonObject

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

        return parseSummaryResponse(
            rawResponse = text,
            usedTokens = response.usageMetadata?.totalTokenCount ?: text.length,
        )
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
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = AiSummarySpec.systemInstruction))),
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
    val responseJsonSchema: JsonObject = AiSummarySpec.geminiResponseSchema,
)

@Serializable
private data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("usageMetadata")
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
private data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int = 0,
    val thoughtsTokenCount: Int? = null,
)
