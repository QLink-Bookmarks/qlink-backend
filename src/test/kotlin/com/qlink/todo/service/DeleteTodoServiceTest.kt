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
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class DeleteTodoServiceTest :
    BaseServiceTest({
        val deleteTodoService = koinGet<DeleteTodoService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("할 일 삭제 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var link: Link
            lateinit var otherUserLink: Link
            lateinit var todo: Todo
            lateinit var otherUserTodo: Todo

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                otherUserLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                todo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                        ),
                    )
                otherUserTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = otherUserLink.id!!,
                            ownerId = otherUser.id!!,
                        ),
                    )
            }

            When("본인 할 일 삭제를") {
                val delete =
                    suspend {
                        deleteTodoService.deleteTodo(user.id!!, todo.id!!)
                    }

                Then("성공하고 조회에서 제외된다") {
                    val todoId = todo.id!!

                    shouldNotThrow<BusinessException> {
                        delete()
                    }

                    todoRepository.findById(todoId) shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val delete =
                    suspend {
                        deleteTodoService.deleteTodo(invalidUserId, todo.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }

            When("없는 할 일 삭제를") {
                val invalidTodoId = RandomFixture.randomId()
                val delete =
                    suspend {
                        deleteTodoService.deleteTodo(user.id!!, invalidTodoId)
                    }

                Then("성공한다") {
                    shouldNotThrow<BusinessException> {
                        delete()
                    }
                }
            }

            When("다른 사용자의 할 일 삭제를") {
                val delete =
                    suspend {
                        deleteTodoService.deleteTodo(user.id!!, otherUserTodo.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_OWNER.message) {
                        delete()
                    }
                }
            }
        }
    })
