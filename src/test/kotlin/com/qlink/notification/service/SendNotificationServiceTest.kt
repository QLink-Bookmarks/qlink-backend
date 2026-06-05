package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.push.client.PushNotificationSendRequest
import com.qlink.push.client.PushNotificationSendResult
import com.qlink.push.client.PushNotificationSender
import com.qlink.push.client.PushNotificationSenderRouter
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.DeviceTokenFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.NotificationFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

class SendNotificationServiceTest :
    BaseServiceTest({
        val tx = koinGet<TransactionRunner>()
        val notificationRepository = koinGet<NotificationRepository>()
        val userRepository = koinGet<UserRepository>()
        val deviceTokenRepository = koinGet<DeviceTokenRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val fcmSender = FakePushNotificationSender(DevicePlatform.WEB)
        val expoSender = FakePushNotificationSender(DevicePlatform.NATIVE)
        val service =
            SendNotificationService(
                tx = tx,
                notificationRepository = notificationRepository,
                userRepository = userRepository,
                deviceTokenRepository = deviceTokenRepository,
                senderRouter = PushNotificationSenderRouter(listOf(fcmSender, expoSender)),
                todoRepository = todoRepository,
            )

        beforeTest {
            fcmSender.reset()
            expoSender.reset()
        }

        Given("푸시 알림 발송 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            suspend fun insertOneTimeTodoNotification(): Number {
                val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                val todo =
                    todoRepository.insert(
                        Todo(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            title = RandomFixture.randomSentenceWithMax(50),
                            reminderAt = Clock.System.now().minus(1.seconds),
                        ),
                    )

                return notificationRepository.insert(Notification.todo(todo)!!).id!!
            }

            When("사용자의 FCM과 Expo 토큰이 모두 성공하면") {
                lateinit var notificationId: Number

                beforeTest {
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.WEB,
                            token = "fcm-success-token",
                        ),
                    )
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.NATIVE,
                            token = "expo-success-token",
                        ),
                    )
                    notificationId = insertOneTimeTodoNotification()
                }

                Then("플랫폼별로 발송하고 성공 건수를 기록한다") {
                    val result = service.send(notificationId.toLong())

                    result.successCount shouldBe 2
                    result.failureCount shouldBe 0

                    fcmSender.requests.map { it.token } shouldBe listOf("fcm-success-token")
                    expoSender.requests.map { it.token } shouldBe listOf("expo-success-token")

                    val saved = notificationRepository.findById(notificationId.toLong())
                    saved shouldNotBe null
                    saved!!.firedAt shouldNotBe null
                    saved.failedAt shouldBe null
                    saved.successCount shouldBe 2
                    saved.failureCount shouldBe 0
                }
            }

            When("일부 토큰 발송에 실패하면") {
                lateinit var notificationId: Number

                beforeTest {
                    fcmSender.failTokens = setOf("fcm-failed-token")
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.WEB,
                            token = "fcm-failed-token",
                        ),
                    )
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.NATIVE,
                            token = "expo-success-token",
                        ),
                    )
                    notificationId = insertOneTimeTodoNotification()
                }

                Then("성공과 실패 건수와 실패 시간을 함께 기록한다") {
                    val result = service.send(notificationId.toLong())

                    result.successCount shouldBe 1
                    result.failureCount shouldBe 1

                    val saved = notificationRepository.findById(notificationId.toLong())
                    saved shouldNotBe null
                    saved!!.firedAt shouldNotBe null
                    saved.failedAt shouldNotBe null
                    saved.successCount shouldBe 1
                    saved.failureCount shouldBe 1
                }
            }

            When("사용자가 리마인더를 허용하지 않으면") {
                lateinit var notificationId: Number

                beforeTest {
                    user = userRepository.insert(UserFixture.createRandomValidUser(allowsReminder = false))
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            token = "not-called-token",
                        ),
                    )
                    notificationId = notificationRepository.insert(NotificationFixture.createRandomNotificationOf(user.id!!)).id!!
                }

                Then("외부 발송 없이 실패 완료 상태를 기록한다") {
                    val result = service.send(notificationId.toLong())

                    result.successCount shouldBe 0
                    result.failureCount shouldBe 0
                    fcmSender.requests shouldBe emptyList()
                    expoSender.requests shouldBe emptyList()

                    val saved = notificationRepository.findById(notificationId.toLong())
                    saved shouldNotBe null
                    saved!!.firedAt shouldBe null
                    saved.failedAt shouldBe null
                }
            }

            When("발송할 토큰이 없으면") {
                lateinit var notificationId: Number

                beforeTest {
                    notificationId = notificationRepository.insert(NotificationFixture.createRandomNotificationOf(user.id!!)).id!!
                }

                Then("외부 발송 없이 실패 완료 상태를 기록한다") {
                    val result = service.send(notificationId.toLong())

                    result.successCount shouldBe 0
                    result.failureCount shouldBe 0

                    val saved = notificationRepository.findById(notificationId.toLong())
                    saved shouldNotBe null
                    saved!!.firedAt shouldBe null
                    saved.failedAt shouldBe null
                }
            }

            When("반복 todo 알림 발송이 성공하면") {
                lateinit var notificationId: Number
                lateinit var todo: Todo

                beforeTest {
                    val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    val repeatTime =
                        Clock.System
                            .now()
                            .toJavaInstant()
                            .atZone(ZoneOffset.UTC)
                            .toLocalTime()
                            .minusMinutes(1)
                            .withSecond(0)
                            .withNano(0)
                    todo =
                        todoRepository.insert(
                            Todo(
                                linkId = link.id!!,
                                ownerId = user.id!!,
                                title = RandomFixture.randomSentenceWithMax(50),
                                reminderAt = Clock.System.now().minus(1.seconds),
                                repeatUntil = Clock.System.now().plus(30.days),
                                repeatDays = RepeatDay.entries.toList(),
                                repeatTime = repeatTime,
                            ),
                        )
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.WEB,
                        ),
                    )
                    notificationId = notificationRepository.insert(Notification.todo(todo)!!).id!!
                }

                Then("다음 반복 알림 notification을 만든다") {
                    service.send(notificationId.toLong())

                    val notifications =
                        notificationRepository.findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = todo.id!!,
                        )

                    notifications.shouldHaveSize(1)
                    notifications.single().willFireAt shouldNotBe todo.reminderAt
                }
            }

            When("1회성 todo 알림 발송이 성공하면") {
                lateinit var notificationId: Number
                lateinit var todo: Todo

                beforeTest {
                    val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    todo =
                        todoRepository.insert(
                            Todo(
                                linkId = link.id!!,
                                ownerId = user.id!!,
                                title = RandomFixture.randomSentenceWithMax(50),
                                reminderAt = Clock.System.now().minus(1.seconds),
                            ),
                        )
                    deviceTokenRepository.insert(
                        DeviceTokenFixture.createRandomValidDeviceToken(
                            userId = user.id!!,
                            platform = DevicePlatform.WEB,
                        ),
                    )
                    notificationId = notificationRepository.insert(Notification.todo(todo)!!).id!!
                }

                Then("다음 notification을 만들지 않는다") {
                    service.send(notificationId.toLong())

                    notificationRepository
                        .findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = todo.id!!,
                        ).shouldHaveSize(0)
                }
            }

            When("대상 알림이 없으면") {
                val send: suspend () -> SendNotificationResult = {
                    service.send(Long.MAX_VALUE)
                }

                Then("비즈니스 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.NOTIFICATION_NOT_FOUND.message) {
                        send()
                    }
                }
            }
        }
    })

private class FakePushNotificationSender(
    override val platform: DevicePlatform,
) : PushNotificationSender {
    var failTokens: Set<String> = emptySet()
    val requests: MutableList<PushNotificationSendRequest> = mutableListOf()

    override suspend fun send(request: PushNotificationSendRequest): PushNotificationSendResult {
        requests.add(request)

        return if (request.token in failTokens) {
            PushNotificationSendResult.failure(errorMessage = "failed")
        } else {
            PushNotificationSendResult.success(messageId = "sent-${request.token}")
        }
    }

    fun reset() {
        failTokens = emptySet()
        requests.clear()
    }
}
