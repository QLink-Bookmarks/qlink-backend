package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.support.truncatedToSecond
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.UpdateTodoRequest
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

class UpdateTodoServiceTest :
    BaseServiceTest({
        val updateTodoService = koinGet<UpdateTodoService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("할 일 수정 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var link: Link
            lateinit var otherLink: Link
            lateinit var todo: Todo
            lateinit var otherUserTodo: Todo
            lateinit var request: UpdateTodoRequest

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                otherLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                todo = todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!))
                otherUserTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = otherLink.id!!,
                            ownerId = otherUser.id!!,
                        ),
                    )
                request = TodoFixture.createValidUpdateTodoRequest(linkId = link.id!!)
            }

            When("본인 할 일 수정을") {
                val update =
                    suspend {
                        updateTodoService.updateTodo(user.id!!, todo.id!!, request)
                    }

                Then("성공한다") {
                    val response = update()
                    val actual = todoRepository.findById(todo.id!!)

                    response.linkId shouldBe request.linkId
                    response.title shouldBe request.title
                    response.reminderAt shouldBe request.reminderAt

                    actual shouldNotBe null
                    actual!!.linkId shouldBe request.linkId
                    actual.title shouldBe request.title
                    actual.reminderAt shouldBe request.reminderAt
                    actual.completedAt shouldBe todo.completedAt
                    actual.updatedAt shouldNotBe todo.updatedAt
                }
            }

            When("다른 본인 링크로 할 일 수정을") {
                val update =
                    suspend {
                        val newLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                        val updateRequest = TodoFixture.createValidUpdateTodoRequest(linkId = newLink.id!!)

                        updateTodoService.updateTodo(user.id!!, todo.id!!, updateRequest)

                        newLink.id
                    }

                Then("성공한다") {
                    val expectedLinkId = update()

                    val actual = todoRepository.findById(todo.id!!)
                    actual!!.linkId shouldBe expectedLinkId
                }
            }

            When("반복 설정이 있는 할 일 수정을") {
                val ignoredReminderAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val repeatUntil = Clock.System.now().plus(30.days)
                lateinit var updateRequest: UpdateTodoRequest
                val update =
                    suspend {
                        updateRequest =
                            UpdateTodoRequest(
                                linkId = todo.linkId,
                                title = RandomFixture.randomSentenceWithMax(50),
                                reminderAt = ignoredReminderAt,
                                repeatUntil = repeatUntil,
                                repeatDays = listOf(RepeatDay.MON, RepeatDay.WED),
                                repeatTime = "09:15",
                                repeatTimezone = "Asia/Seoul",
                            )
                        updateTodoService.updateTodo(user.id!!, todo.id!!, updateRequest)
                    }

                Then("응답과 저장 데이터에 반복 설정을 반영한다") {
                    val response = update()
                    val actual = todoRepository.findById(todo.id!!)

                    response.reminderAt shouldNotBe ignoredReminderAt
                    response.repeatUntil.truncatedToSecond() shouldBe repeatUntil.truncatedToSecond()
                    response.repeatDays shouldBe updateRequest.repeatDays
                    response.repeatTime shouldBe "09:15"

                    actual shouldNotBe null
                    actual!!.reminderAt shouldNotBe ignoredReminderAt
                    actual.reminderAt shouldNotBe null
                    actual.repeatUntil.truncatedToSecond() shouldBe repeatUntil.truncatedToSecond()
                    actual.repeatDays shouldBe updateRequest.repeatDays
                    actual.repeatTime.toString() shouldBe "09:15"
                    actual.repeatTimezone?.id shouldBe "Asia/Seoul"
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val update =
                    suspend {
                        updateTodoService.updateTodo(invalidUserId, todo.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("할 일이 없으면") {
                val invalidTodoId = RandomFixture.randomId()
                val update =
                    suspend {
                        updateTodoService.updateTodo(user.id!!, invalidTodoId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("본인 소유 할 일이 아니면") {
                val update =
                    suspend {
                        updateTodoService.updateTodo(user.id!!, otherUserTodo.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }

            When("요청 링크가 다르고 링크가 없으면") {
                val updateRequest = TodoFixture.createValidUpdateTodoRequest(linkId = RandomFixture.randomId())
                val update =
                    suspend {
                        updateTodoService.updateTodo(user.id!!, todo.id!!, updateRequest)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_LINK_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("요청 링크가 다르고 본인 소유 링크가 아니면") {
                val updateRequest = TodoFixture.createValidUpdateTodoRequest(linkId = otherLink.id!!)
                val update =
                    suspend {
                        updateTodoService.updateTodo(user.id!!, todo.id!!, updateRequest)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }
        }
    })
