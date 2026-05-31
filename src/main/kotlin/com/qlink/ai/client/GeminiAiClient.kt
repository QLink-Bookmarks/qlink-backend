package com.qlink.ai.client

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

class GeminiAiClient(
    private val httpClient: HttpClient,
    private val config: AiClientConfig,
) : AiClient {
    override val provider: AiProvider = AiProvider.GEMINI

    override suspend fun summarize(prompt: AiSummaryPrompt): String {
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.AI_API_KEY_MISSING)
        val response =
            httpClient
                .post("${config.baseUrl}/v1beta/models/${config.model}:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(
                        GeminiGenerateContentRequest(
                            contents =
                                listOf(
                                    GeminiContent(
                                        parts =
                                            listOf(
                                                GeminiPart(text = prompt.toInstruction()),
                                            ),
                                    ),
                                ),
                        ),
                    )
                }.body<GeminiGenerateContentResponse>()

        return response
            .candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text?.trim()?.takeIf(String::isNotBlank) }
            ?: throw BusinessException(ErrorCode.AI_EMPTY_RESPONSE)
    }

    private fun AiSummaryPrompt.toInstruction(): String =
        """
        아래 링크 정보를 바탕으로 한국어로 짧고 읽기 쉬운 요약을 작성해줘.
        제목: $title
        URL: $url
        메모: ${memo.orEmpty()}
        태그: ${tags.joinToString(", ")}
        요약은 3문장 이내로 작성하고, 추측은 피해서 답해줘.
        """.trimIndent()
}

@Serializable
private data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
)

@Serializable
private data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason")
    val finishReason: String? = null,
)
