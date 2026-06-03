package com.qlink.push.client

import com.qlink.device.domain.DevicePlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val EXPO_PUSH_SEND_URL = "https://exp.host/--/api/v2/push/send"

class ExpoPushClient(
    private val httpClient: HttpClient,
    private val accessToken: String? = null,
) : PushNotificationSender {
    override val platform: DevicePlatform = DevicePlatform.NATIVE

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult =
        runCatching {
            httpClient
                .post(EXPO_PUSH_SEND_URL) {
                    accessToken?.takeIf(String::isNotBlank)?.let { bearerAuth(it) }
                    contentType(ContentType.Application.Json)
                    setBody(ExpoPushSendRequest.from(request))
                }.body<ExpoPushSendResponse>()
        }.fold(
            onSuccess = { it.toPushNotificationSendResult() },
            onFailure = { PushNotificationSendResult.failure(errorMessage = it.message) },
        )
}

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
