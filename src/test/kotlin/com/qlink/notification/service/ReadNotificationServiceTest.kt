package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.NotificationFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock

class ReadNotificationServiceTest :
    BaseServiceTest({
        val service = koinGet<ReadNotificationService>()
        val notificationRepository = koinGet<NotificationRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("알림 읽음처리 테스트") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("발송 완료된 미읽음 알림을 읽음처리하면") {
                lateinit var notificationId: Number

                beforeTest {
                    notificationId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                }

                Then("읽음 시간이 기록된다") {
                    service.readNotification(
                        loginId = user.id!!,
                        notificationId = notificationId.toLong(),
                    )

                    notificationRepository.findById(notificationId.toLong())!!.readAt shouldNotBe null
                }
            }

            When("이미 읽은 알림을 읽음처리하면") {
                lateinit var notificationId: Number

                beforeTest {
                    notificationId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    firedAt = Clock.System.now(),
                                    readAt = Clock.System.now(),
                                ),
                            ).id!!
                }
                val read =
                    suspend {
                        service.readNotification(
                            loginId = user.id!!,
                            notificationId = notificationId.toLong(),
                        )
                    }

                Then("예외 없이 성공한다") {
                    shouldNotThrow<Throwable> {
                        read()
                    }
                }
            }

            When("없는 알림을 읽음처리하면") {
                val read =
                    suspend {
                        service.readNotification(
                            loginId = user.id!!,
                            notificationId = RandomFixture.randomId(),
                        )
                    }

                Then("예외 없이 성공한다") {
                    shouldNotThrow<Throwable> {
                        read()
                    }
                }
            }

            When("다른 사용자의 알림을 읽음처리하면") {
                lateinit var notificationId: Number

                beforeTest {
                    notificationId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = otherUser.id!!,
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                }
                val read =
                    suspend {
                        service.readNotification(
                            loginId = user.id!!,
                            notificationId = notificationId.toLong(),
                        )
                    }

                Then("예외 없이 성공하고 읽음 시간이 바뀌지 않는다") {
                    shouldNotThrow<Throwable> {
                        read()
                    }

                    notificationRepository.findById(notificationId.toLong())!!.readAt shouldBe null
                }
            }

            When("아직 발송되지 않은 알림을 읽음처리하면") {
                lateinit var notificationId: Number

                beforeTest {
                    notificationId =
                        notificationRepository
                            .insert(NotificationFixture.createRandomNotificationOf(userId = user.id!!))
                            .id!!
                }
                val read =
                    suspend {
                        service.readNotification(
                            loginId = user.id!!,
                            notificationId = notificationId.toLong(),
                        )
                    }

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.NOTIFICATION_NOT_FIRED.message) {
                        read()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val read =
                    suspend {
                        service.readNotification(
                            loginId = RandomFixture.randomId(),
                            notificationId = RandomFixture.randomId(),
                        )
                    }

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        read()
                    }
                }
            }
        }
    })
