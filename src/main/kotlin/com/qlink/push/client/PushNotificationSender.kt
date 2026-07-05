package com.qlink.push.client

import com.qlink.device.domain.DevicePlatform

interface PushNotificationSender {
    val platform: DevicePlatform

    suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult

    // Multicast a chunk that shares the same title/message/data (e.g. one announcement).
    // Default falls back to per-token sends; platform clients override with a true batch call.
    suspend fun sendMulticast(requests: List<PushNotificationSendRequest>): List<PushNotificationSendResult> = requests.map { send(it) }
}

data class PushNotificationSendRequest(
    val token: String,
    val title: String,
    val message: String,
    val data: Map<String, String> = emptyMap(),
)

data class PushNotificationSendResult(
    val success: Boolean,
    val messageId: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(messageId: String? = null): PushNotificationSendResult =
            PushNotificationSendResult(
                success = true,
                messageId = messageId,
            )

        fun failure(errorMessage: String? = null): PushNotificationSendResult =
            PushNotificationSendResult(
                success = false,
                errorMessage = errorMessage,
            )
    }
}
