package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.notification.service.GetNotificationsService
import com.qlink.post.dto.CreatePostRequest
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class CreatePostServiceTest :
    BaseServiceTest({
        val service = koinGet<CreatePostService>()
        val userRepository = koinGet<UserRepository>()
        val notificationRepository = koinGet<NotificationRepository>()
        val getNotificationsService = koinGet<GetNotificationsService>()

        Given("게시글 생성 서비스 테스트") {
            When("일반 사용자가 FEEDBACK 게시글을 생성하면") {
                Then("성공한다") {
                    val user = userRepository.insert(UserFixture.createRandomValidUser())

                    val response =
                        service.createPost(
                            loginId = user.id!!,
                            role = Role.NORMAL,
                            request = CreatePostRequest(title = "피드백", contents = "내용", type = "feedback"),
                        )

                    (response.id > 0) shouldBe true
                }
            }

            When("일반 사용자가 ANNOUNCEMENT 게시글을 생성하면") {
                Then("권한 예외를 반환한다") {
                    val user = userRepository.insert(UserFixture.createRandomValidUser())

                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_ANNOUNCEMENT_FORBIDDEN.message) {
                        service.createPost(
                            loginId = user.id!!,
                            role = Role.NORMAL,
                            request = CreatePostRequest(title = "공지", contents = "내용", type = "ANNOUNCEMENT"),
                        )
                    }
                }
            }

            When("ADMIN 이 ANNOUNCEMENT 게시글을 생성하면") {
                Then("전 사용자에게 ANNOUNCE 알림을 발송한다") {
                    val admin = userRepository.insert(UserFixture.createRandomValidUser())
                    val other = userRepository.insert(UserFixture.createRandomValidUser())

                    val response =
                        service.createPost(
                            loginId = admin.id!!,
                            role = Role.ADMIN,
                            request =
                                CreatePostRequest(
                                    title = "에이링크 소식",
                                    contents = "내용",
                                    type = "announcement",
                                    imageUrls = listOf("https://images.archivelink.app/a.png"),
                                ),
                        )

                    val notified =
                        notificationRepository
                            .findByContext(NotificationContext.ANNOUNCE, response.id)
                            .map { it.userId }
                    notified shouldContainAll listOf(admin.id, other.id)

                    val announceOnly =
                        getNotificationsService.getNotifications(
                            loginId = other.id!!,
                            query = null,
                            type = "announce",
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )
                    announceOnly.contents.all { it.context == NotificationContext.ANNOUNCE } shouldBe true
                    announceOnly.contents.isNotEmpty() shouldBe true
                }
            }
        }
    })
