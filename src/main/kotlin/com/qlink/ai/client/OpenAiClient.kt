package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

        return parseSummaryResponse(
            rawResponse = text,
            usedTokens = response.usage?.totalTokens ?: text.length,
        )
    }

    override suspend fun validateApiKey(request: AiApiKeyValidationRequest) {
        val response =
            httpClient.get(request.baseUrl.openAiModelsUrl()) {
                bearerAuth(request.apiKey)
            }

        if (response.status.value !in 200..299) {
            throw AiApiKeyValidationException(response.status.value)
        }
    }
}

private fun String.openAiModelsUrl(): String =
    removeSuffix("/responses")
        .removeSuffix("/")
        .let { "$it/models" }

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
                instructions = AiSummarySpec.systemInstruction,
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
    val name: String = AiSummarySpec.RESPONSE_SCHEMA_NAME,
    val strict: Boolean = true,
    val schema: JsonObject = AiSummarySpec.openAiJsonSchema,
)

@Serializable
private data class OpenAiResponsesResponse(
    val output: List<OpenAiOutput> = emptyList(),
    val usage: OpenAiUsage? = null,
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

@Serializable
private data class OpenAiUsage(
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
)
