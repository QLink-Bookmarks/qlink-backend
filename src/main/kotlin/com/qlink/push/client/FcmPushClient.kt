package com.qlink.push.client

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.qlink.device.domain.DevicePlatform

class FcmPushClient(
    private val firebaseInitializer: FirebaseInitializer,
    private val firebaseMessagingProvider: () -> FirebaseMessaging = { FirebaseMessaging.getInstance() },
) : PushNotificationSender {
    override val platform: DevicePlatform = DevicePlatform.WEB

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult =
        runCatching {
            firebaseInitializer.requireInitialized()
            firebaseMessagingProvider().send(request.toFcmMessage())
        }.fold(
            onSuccess = { PushNotificationSendResult.success(messageId = it) },
            onFailure = { PushNotificationSendResult.failure(errorMessage = it.message) },
        )

    override suspend fun sendMulticast(requests: List<PushNotificationSendRequest>): List<PushNotificationSendResult> {
        if (requests.isEmpty()) return emptyList()

        return runCatching {
            firebaseInitializer.requireInitialized()
            firebaseMessagingProvider().sendEachForMulticast(requests.toMulticastMessage())
        }.fold(
            onSuccess = { batch ->
                batch.responses.map { response ->
                    if (response.isSuccessful) {
                        PushNotificationSendResult.success(messageId = response.messageId)
                    } else {
                        PushNotificationSendResult.failure(errorMessage = response.exception?.message)
                    }
                }
            },
            onFailure = { error -> requests.map { PushNotificationSendResult.failure(errorMessage = error.message) } },
        )
    }
}

private fun List<PushNotificationSendRequest>.toMulticastMessage(): MulticastMessage {
    val head = first()
    return MulticastMessage
        .builder()
        .addAllTokens(map { it.token })
        .setNotification(
            Notification
                .builder()
                .setTitle(head.title)
                .setBody(head.message)
                .build(),
        ).putAllData(head.data)
        .build()
}

private fun PushNotificationSendRequest.toFcmMessage(): Message =
    Message
        .builder()
        .setToken(token)
        .setNotification(
            Notification
                .builder()
                .setTitle(title)
                .setBody(message)
                .build(),
        ).putAllData(data)
        .build()
