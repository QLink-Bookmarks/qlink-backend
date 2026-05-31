package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenAiClient(
    private val httpClient: HttpClient,
) : AiClient {
    override val providerType: AiProviderType = AiProviderType.OPENAI

    override suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse {
        val response =
            httpClient
                .post(request.baseUrl) {
                    bearerAuth(request.apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(OpenAiResponsesRequest.from(request.model, request.prompt))
                }.body<OpenAiResponsesResponse>()
        val text =
            response
                .output
                .flatMap { it.content }
                .firstNotNullOfOrNull { it.text?.trim()?.takeIf(String::isNotBlank) }
                ?: throw BusinessException(ErrorCode.AI_EMPTY_RESPONSE)

        return parseSummaryResponse(rawResponse = text)
    }
}

@Serializable
private data class OpenAiResponsesRequest(
    val model: String,
    val instructions: String,
    val input: List<OpenAiInput>,
    val tools: List<OpenAiTool>,
    val text: OpenAiText,
) {
    companion object {
        fun from(
            model: String,
            prompt: String,
        ): OpenAiResponsesRequest =
            OpenAiResponsesRequest(
                model = model,
                instructions = systemInstruction,
                input =
                    listOf(
                        OpenAiInput(
                            role = "user",
                            content = listOf(OpenAiContent(type = "input_text", text = prompt)),
                        ),
                    ),
                tools = listOf(OpenAiTool(type = "web_search")),
                text = OpenAiText(format = OpenAiFormat()),
            )
    }
}

@Serializable
private data class OpenAiInput(
    val role: String,
    val content: List<OpenAiContent>,
)

@Serializable
private data class OpenAiContent(
    val type: String,
    val text: String,
)

@Serializable
private data class OpenAiTool(
    val type: String,
)

@Serializable
private data class OpenAiText(
    val format: OpenAiFormat,
)

@Serializable
private data class OpenAiFormat(
    val type: String = "json_schema",
    val name: String = "qlink_bookmark_summary_result",
    val strict: Boolean = true,
    val schema: Map<String, String> = emptyMap(),
)

@Serializable
private data class OpenAiResponsesResponse(
    val output: List<OpenAiOutput> = emptyList(),
)

@Serializable
private data class OpenAiOutput(
    val content: List<OpenAiOutputContent> = emptyList(),
)

@Serializable
private data class OpenAiOutputContent(
    val type: String? = null,
    @SerialName("text")
    val text: String? = null,
)
