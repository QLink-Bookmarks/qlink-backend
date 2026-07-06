package com.qlink.notification.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.push.client.PushNotificationSendRequest
import com.qlink.push.client.PushNotificationSendResult
import com.qlink.push.client.PushNotificationSender
import com.qlink.push.client.PushNotificationSenderRouter
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private class RecordingSender(
    override val platform: DevicePlatform,
) : PushNotificationSender {
    val multicasts = mutableListOf<List<PushNotificationSendRequest>>()

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult = PushNotificationSendResult.success()

    override suspend fun sendMulticast(requests: List<PushNotificationSendRequest>): List<PushNotificationSendResult> {
        multicasts += requests
        return requests.map { PushNotificationSendResult.success() }
    }
}

class SendAnnouncementServiceTest :
    BaseServiceTest({
        val tx = koinGet<TransactionRunner>()
        val userRepository = koinGet<UserRepository>()
        val notificationRepository = koinGet<NotificationRepository>()
        val deviceTokenRepository = koinGet<DeviceTokenRepository>()

        Given("공지 발송 서비스 테스트") {
            When("공지를 발송하면") {
                Then("전 사용자에게 인앱 알림을 남기고 디바이스 토큰으로 멀티캐스트한다") {
                    val sender = RecordingSender(DevicePlatform.NATIVE)
                    val service =
                        SendAnnouncementService(
                            tx = tx,
                            userRepository = userRepository,
                            notificationRepository = notificationRepository,
                            deviceTokenRepository = deviceTokenRepository,
                            senderRouter = PushNotificationSenderRouter(listOf(sender)),
                        )

                    val withDevice = userRepository.insert(UserFixture.createRandomValidUser())
                    val withoutDevice = userRepository.insert(UserFixture.createRandomValidUser())
                    deviceTokenRepository.insert(
                        DeviceToken(userId = withDevice.id!!, platform = DevicePlatform.NATIVE, token = "expo-token-1"),
                    )

                    service.sendForPost(postId = 12_345L, postTitle = "새 공지")

                    val notifiedUsers =
                        notificationRepository
                            .findByContext(NotificationContext.ANNOUNCE, 12_345L)
                            .map { it.userId }
                    notifiedUsers shouldContainAll listOf(withDevice.id, withoutDevice.id)

                    val pushedTokens = sender.multicasts.flatten().map { it.token }
                    pushedTokens shouldContainExactly listOf("expo-token-1")
                    sender.multicasts
                        .first()
                        .first()
                        .title shouldBe "에이링크 공지사항"
                }
            }
        }
    })
