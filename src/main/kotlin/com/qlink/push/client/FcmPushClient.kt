package com.qlink.push.client

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.qlink.device.domain.DevicePlatform

class FcmPushClient(
    private val firebaseMessaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) : PushNotificationSender {
    override val platform: DevicePlatform = DevicePlatform.WEB

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult =
        runCatching {
            firebaseMessaging.send(request.toFcmMessage())
        }.fold(
            onSuccess = { PushNotificationSendResult.success(messageId = it) },
            onFailure = { PushNotificationSendResult.failure(errorMessage = it.message) },
        )
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
