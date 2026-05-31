package com.qlink.ai.client

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class OpenAiClient(
    private val httpClient: HttpClient,
    private val config: AiClientConfig,
) : AiClient {
    override val provider: AiProvider = AiProvider.OPENAI

    override suspend fun summarize(prompt: AiSummaryPrompt): String {
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.AI_API_KEY_MISSING)
        val response =
            httpClient
                .post("${config.baseUrl}/v1/chat/completions") {
                    bearerAuth(apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(
                        OpenAiChatCompletionRequest(
                            model = config.model,
                            messages =
                                listOf(
                                    OpenAiMessage(
                                        role = "system",
                                        content = "너는 북마크 링크를 한국어로 간결하게 요약하는 도우미야.",
                                    ),
                                    OpenAiMessage(
                                        role = "user",
                                        content = prompt.toInstruction(),
                                    ),
                                ),
                        ),
                    )
                }.body<OpenAiChatCompletionResponse>()

        return response
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw BusinessException(ErrorCode.AI_EMPTY_RESPONSE)
    }

    private fun AiSummaryPrompt.toInstruction(): String =
        """
        아래 링크 정보를 바탕으로 3문장 이내의 요약을 작성해줘.
        제목: $title
        URL: $url
        메모: ${memo.orEmpty()}
        태그: ${tags.joinToString(", ")}
        확인되지 않은 내용은 단정하지 마.
        """.trimIndent()
}

@Serializable
private data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
)

@Serializable
private data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiChatCompletionResponse(
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage? = null,
)
