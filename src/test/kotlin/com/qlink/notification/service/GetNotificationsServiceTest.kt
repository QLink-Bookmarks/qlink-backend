package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.NotificationFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class GetNotificationsServiceTest :
    BaseServiceTest({
        val service = koinGet<GetNotificationsService>()
        val notificationRepository = koinGet<NotificationRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("알림 목록 조회 테스트") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("발송 완료된 알림들이 있으면") {
                lateinit var oldNotificationId: Number
                lateinit var newNotificationId: Number

                beforeTest {
                    oldNotificationId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                    newNotificationId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                    notificationRepository.insert(NotificationFixture.createRandomNotificationOf(userId = user.id!!))
                    notificationRepository.insert(
                        NotificationFixture.createRandomNotificationOf(
                            userId = otherUser.id!!,
                            firedAt = Clock.System.now(),
                        ),
                    )
                }

                Then("로그인 사용자의 발송 완료 알림만 최신순으로 반환한다") {
                    val response =
                        service.getNotifications(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )

                    response.contents.map { it.id } shouldContainExactly
                        listOf(newNotificationId.toLong(), oldNotificationId.toLong())
                    response.hasNext shouldBe false
                }
            }

            When("검색어가 제목 또는 메시지에 포함되면") {
                lateinit var titleMatchedId: Number
                lateinit var messageMatchedId: Number

                beforeTest {
                    val keyword = "needle-${RandomFixture.randomId()}"
                    titleMatchedId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    title = "title $keyword",
                                    message = RandomFixture.randomSentenceWithMax(200),
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                    messageMatchedId =
                        notificationRepository
                            .insert(
                                NotificationFixture.createRandomNotificationOf(
                                    userId = user.id!!,
                                    title = RandomFixture.randomSentenceWithMax(50),
                                    message = "message $keyword",
                                    firedAt = Clock.System.now(),
                                ),
                            ).id!!
                    notificationRepository.insert(
                        NotificationFixture.createRandomNotificationOf(
                            userId = user.id!!,
                            title = RandomFixture.randomSentenceWithMax(50),
                            message = RandomFixture.randomSentenceWithMax(200),
                            firedAt = Clock.System.now(),
                        ),
                    )
                }

                Then("매칭된 알림만 반환한다") {
                    val keyword = "needle"
                    val response =
                        service.getNotifications(
                            loginId = user.id!!,
                            query = keyword,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )

                    response.contents.map { it.id } shouldContainExactly
                        listOf(messageMatchedId.toLong(), titleMatchedId.toLong())
                }
            }

            When("페이지 크기보다 알림이 많으면") {
                beforeTest {
                    repeat(3) {
                        notificationRepository.insert(
                            NotificationFixture.createRandomNotificationOf(
                                userId = user.id!!,
                                firedAt = Clock.System.now(),
                            ),
                        )
                    }
                }

                Then("커서로 다음 페이지를 조회한다") {
                    val firstPage =
                        service.getNotifications(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 2),
                        )

                    val secondPage =
                        service.getNotifications(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest =
                                ScrollRequest(
                                    cursor = firstPage.nextCursor,
                                    size = 2,
                                ),
                        )

                    firstPage.contents.size shouldBe 2
                    firstPage.hasNext shouldBe true
                    secondPage.contents.size shouldBe 1
                    secondPage.hasNext shouldBe false
                }
            }

            When("로그인 사용자가 없으면") {
                val getNotifications =
                    suspend {
                        service.getNotifications(
                            loginId = RandomFixture.randomId(),
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )
                    }

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        getNotifications()
                    }
                }
            }
        }
    })
