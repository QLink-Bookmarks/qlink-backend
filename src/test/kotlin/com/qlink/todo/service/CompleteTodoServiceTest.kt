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
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.toKotlinInstant

class CompleteTodoServiceTest :
    BaseServiceTest({
        val completeTodoService = koinGet<CompleteTodoService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("할 일 완료 상태 변경 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var link: Link
            lateinit var otherLink: Link
            lateinit var incompleteTodo: Todo
            lateinit var completedTodo: Todo
            lateinit var otherUserTodo: Todo

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                otherLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                incompleteTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            completedAt = null,
                        ),
                    )
                completedTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            completedAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
                        ),
                    )
                otherUserTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = otherLink.id!!,
                            ownerId = otherUser.id!!,
                        ),
                    )
            }

            When("미완료 할 일을 완료하면") {
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = true)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, incompleteTodo.id!!, request)
                    }

                Then("완료 시간이 저장된다") {
                    val response = complete()
                    val actual = todoRepository.findById(incompleteTodo.id!!)

                    response.completeAt shouldNotBe null
                    actual shouldNotBe null
                    actual!!.completedAt shouldBe response.completeAt
                    actual.isCompleted shouldBe true
                }
            }

            When("완료된 할 일을 다시 완료하면") {
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = true)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, completedTodo.id!!, request)
                    }

                Then("기존 완료 시간이 유지된다") {
                    val response = complete()
                    val actual = todoRepository.findById(completedTodo.id!!)

                    response.completeAt shouldBe completedTodo.completedAt
                    actual!!.completedAt shouldBe completedTodo.completedAt
                    actual.updatedAt shouldBe completedTodo.updatedAt
                }
            }

            When("완료된 할 일을 미완료하면") {
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = false)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, completedTodo.id!!, request)
                    }

                Then("완료 시간이 제거된다") {
                    val response = complete()
                    val actual = todoRepository.findById(completedTodo.id!!)

                    response.completeAt shouldBe null
                    actual!!.completedAt shouldBe null
                    actual.isCompleted shouldBe false
                }
            }

            When("미완료 할 일을 다시 미완료하면") {
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = false)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, incompleteTodo.id!!, request)
                    }

                Then("미완료 상태가 유지된다") {
                    val response = complete()
                    val actual = todoRepository.findById(incompleteTodo.id!!)

                    response.completeAt shouldBe null
                    actual!!.completedAt shouldBe null
                    actual.updatedAt shouldBe incompleteTodo.updatedAt
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = true)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(invalidUserId, incompleteTodo.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        complete()
                    }
                }
            }

            When("할 일이 없으면") {
                val invalidTodoId = RandomFixture.randomId()
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = true)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, invalidTodoId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_NOT_FOUND.message) {
                        complete()
                    }
                }
            }

            When("본인 소유 할 일이 아니면") {
                val request = TodoFixture.createCompleteTodoRequest(isCompleted = true)
                val complete =
                    suspend {
                        completeTodoService.completeTodo(user.id!!, otherUserTodo.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_OWNER.message) {
                        complete()
                    }
                }
            }
        }
    })
