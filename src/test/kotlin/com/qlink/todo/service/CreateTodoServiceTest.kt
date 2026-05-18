package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.toKotlinInstant

class CreateTodoServiceTest :
    BaseServiceTest({
        val createTodoService = koinGet<CreateTodoService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

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
                        reminderAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
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
