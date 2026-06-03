package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.support.truncatedToSecond
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinInstant

class CreateTodoServiceTest :
    BaseServiceTest({
        val createTodoService = koinGet<CreateTodoService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val notificationRepository = koinGet<NotificationRepository>()

        Given("할 일 생성 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
            }

            When("할 일 생성을") {
                val request =
                    CreateTodoRequest(
                        linkId = link.id!!,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = RandomFixture.futureDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant(),
                    )
                val expectedOwnerId = user.id!!
                val expected = createTodoService.createTodo(user.id!!, request)

                Then("성공한다") {
                    val actual = todoRepository.findById(expected.id)

                    actual shouldNotBe null
                    actual!!.id shouldBe expected.id
                    actual.linkId shouldBe request.linkId
                    actual.ownerId shouldBe expectedOwnerId
                    actual.title shouldBe request.title
                    actual.reminderAt shouldBe request.reminderAt
                    actual.completedAt shouldBe null
                    actual.isCompleted shouldBe false

                    val notification =
                        notificationRepository
                            .findPendingByContext(
                                context = NotificationContext.TODO,
                                contextId = actual.id!!,
                            ).single()
                    notification.willFireAt shouldBe actual.reminderAt
                }
            }

            When("반복 설정이 있는 할 일 생성을") {
                val ignoredReminderAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val repeatUntil = Clock.System.now().plus(30.days)
                val request =
                    CreateTodoRequest(
                        linkId = link.id!!,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = ignoredReminderAt,
                        repeatUntil = repeatUntil,
                        repeatDays = RepeatDay.entries.toList(),
                        repeatTime = "23:59",
                    )
                val expected = createTodoService.createTodo(user.id!!, request)

                Then("알림을 다음 반복 시각으로 계산하고 UTC 시간대를 저장한다") {
                    val actual = todoRepository.findById(expected.id)

                    actual shouldNotBe null
                    actual!!.reminderAt shouldNotBe ignoredReminderAt
                    actual.reminderAt shouldNotBe null
                    actual.repeatUntil.truncatedToSecond() shouldBe repeatUntil.truncatedToSecond()
                    actual.repeatDays shouldBe RepeatDay.entries.toList()
                    actual.repeatTime.toString() shouldBe "23:59"
                    actual.repeatTimezone?.id shouldBe "UTC"

                    val notification =
                        notificationRepository
                            .findPendingByContext(
                                context = NotificationContext.TODO,
                                contextId = actual.id!!,
                            ).single()
                    notification.willFireAt shouldBe actual.reminderAt
                    notification.willFireAt shouldNotBe ignoredReminderAt
                }
            }

            When("반복 종료 시각이 지나 다음 알림이 없으면") {
                val request =
                    CreateTodoRequest(
                        linkId = link.id!!,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant(),
                        repeatUntil = Clock.System.now().minus(1.days),
                        repeatDays = RepeatDay.entries.toList(),
                        repeatTime = "23:59",
                    )
                val expected = createTodoService.createTodo(user.id!!, request)

                Then("notification을 만들지 않는다") {
                    val actual = todoRepository.findById(expected.id)

                    actual shouldNotBe null
                    actual!!.reminderAt shouldBe null
                    notificationRepository
                        .findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = actual.id!!,
                        ).size shouldBe 0
                }
            }

            When("반복 설정 없는 알림 시간이 과거이면") {
                val create =
                    suspend {
                        val request =
                            CreateTodoRequest(
                                linkId = link.id!!,
                                title = RandomFixture.randomSentenceWithMax(50),
                                reminderAt = Clock.System.now().minus(1.days),
                            )
                        createTodoService.createTodo(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REMINDER_AT_INVALID.message) {
                        create()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val request =
                    CreateTodoRequest(
                        linkId = link.id!!,
                        title = RandomFixture.randomSentenceWithMax(50),
                    )
                val create =
                    suspend {
                        createTodoService.createTodo(invalidUserId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("링크가 없으면") {
                val request =
                    CreateTodoRequest(
                        linkId = RandomFixture.randomId(),
                        title = RandomFixture.randomSentenceWithMax(50),
                    )
                val create =
                    suspend {
                        createTodoService.createTodo(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_LINK_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("본인 소유 링크가 아니면") {
                val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                val otherLink =
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                val request =
                    CreateTodoRequest(
                        linkId = otherLink.id!!,
                        title = RandomFixture.randomSentenceWithMax(50),
                    )
                val create =
                    suspend {
                        createTodoService.createTodo(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        create()
                    }
                }
            }
        }
    })
