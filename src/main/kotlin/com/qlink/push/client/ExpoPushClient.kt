package com.qlink.push.client

import com.qlink.device.domain.DevicePlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow

private const val DEFAULT_MAX_EXPO_SEND_ATTEMPTS = 10
private const val INITIAL_RETRY_DELAY_MS = 100L

class ExpoPushClient(
    private val httpClient: HttpClient,
    private val sendUrl: String,
    private val accessToken: String? = null,
    private val maxAttempts: Int = DEFAULT_MAX_EXPO_SEND_ATTEMPTS,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : PushNotificationSender {
    override val platform: DevicePlatform = DevicePlatform.NATIVE

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult =
        runCatching {
            val response =
                retryExpoSend(maxAttempts = maxAttempts, delayProvider = delayProvider) {
                    httpClient.post(sendUrl) {
                        accessToken?.takeIf(String::isNotBlank)?.let { bearerAuth(it) }
                        contentType(ContentType.Application.Json)
                        setBody(ExpoPushSendRequest.from(request))
                    }
                }
            check(response.status.isSuccess()) {
                "Expo push request failed with status ${response.status.value}."
            }
            response.body<ExpoPushSendResponse>()
        }.fold(
            onSuccess = { it.toPushNotificationSendResult() },
            onFailure = { PushNotificationSendResult.failure(errorMessage = it.message) },
        )
}

private suspend fun retryExpoSend(
    maxAttempts: Int,
    delayProvider: suspend (Long) -> Unit,
    block: suspend () -> HttpResponse,
): HttpResponse {
    repeat(maxAttempts.coerceAtLeast(1)) { attemptIndex ->
        val response = block()
        val isLastAttempt = attemptIndex == maxAttempts.coerceAtLeast(1) - 1
        if (!response.status.isExpoRetryable() || isLastAttempt) {
            return response
        }

        delayProvider(INITIAL_RETRY_DELAY_MS * 2.0.pow(attemptIndex).toLong())
    }

    error("Unreachable Expo retry state.")
}

private fun HttpStatusCode.isExpoRetryable(): Boolean = this == HttpStatusCode.TooManyRequests || value in 500..599

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

@Serializable
private data class ExpoPushSendRequest(
    val to: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
) {
    companion object {
        fun from(request: PushNotificationSendRequest): ExpoPushSendRequest =
            ExpoPushSendRequest(
                to = request.token,
                title = request.title,
                body = request.message,
                data = request.data,
            )
    }
}

@Serializable
private data class ExpoPushSendResponse(
    val data: ExpoPushTicket? = null,
)

@Serializable
private data class ExpoPushTicket(
    val status: String,
    val id: String? = null,
    val message: String? = null,
    val details: ExpoPushTicketDetails? = null,
) {
    fun toPushNotificationSendResult(): PushNotificationSendResult =
        when (status) {
            "ok" -> PushNotificationSendResult.success(messageId = id)
            else -> PushNotificationSendResult.failure(errorMessage = message ?: details?.error)
        }
}

@Serializable
private data class ExpoPushTicketDetails(
    @SerialName("error")
    val error: String? = null,
)

private fun ExpoPushSendResponse.toPushNotificationSendResult(): PushNotificationSendResult =
    data?.toPushNotificationSendResult()
        ?: PushNotificationSendResult.failure(errorMessage = "Expo push response data is empty.")
