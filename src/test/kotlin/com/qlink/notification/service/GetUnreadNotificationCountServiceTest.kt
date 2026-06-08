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
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class GetUnreadNotificationCountServiceTest :
    BaseServiceTest({
        val service = koinGet<GetUnreadNotificationCountService>()
        val notificationRepository = koinGet<NotificationRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("안 읽은 알림 집계 테스트") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())

                notificationRepository.insert(
                    NotificationFixture.createRandomNotificationOf(
                        userId = user.id!!,
                        firedAt = Clock.System.now(),
                    ),
                )
                notificationRepository.insert(
                    NotificationFixture.createRandomNotificationOf(
                        userId = user.id!!,
                        firedAt = Clock.System.now(),
                        readAt = Clock.System.now(),
                    ),
                )
                notificationRepository.insert(NotificationFixture.createRandomNotificationOf(userId = user.id!!))
                notificationRepository.insert(
                    NotificationFixture.createRandomNotificationOf(
                        userId = user.id!!,
                        failedAt = Clock.System.now(),
                    ),
                )
                notificationRepository.insert(
                    NotificationFixture.createRandomNotificationOf(
                        userId = otherUser.id!!,
                        firedAt = Clock.System.now(),
                    ),
                )
            }

            When("안 읽은 알림 수를 조회하면") {
                Then("발송 완료 미읽음 알림만 집계한다") {
                    val response = service.getUnreadCount(user.id!!)

                    response.unreadCount shouldBe 1
                }
            }

            When("로그인 사용자가 없으면") {
                val getUnreadCount =
                    suspend {
                        service.getUnreadCount(loginId = RandomFixture.randomId())
                    }

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        getUnreadCount()
                    }
                }
            }
        }
    })
