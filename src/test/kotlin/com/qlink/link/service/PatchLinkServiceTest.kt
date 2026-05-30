package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.toKotlinInstant

class PatchLinkServiceTest :
    BaseServiceTest({
        val patchLinkService = koinGet<PatchLinkService>()
        val tx = koinGet<TransactionRunner>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("링크 부분 수정 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder
            lateinit var otherFolder: Folder
            lateinit var link: Link
            lateinit var otherLink: Link
            lateinit var firstTodo: Todo
            lateinit var secondTodo: Todo
            lateinit var otherUserTodo: Todo
            lateinit var otherLinkTodo: Todo

            beforeTest {
                tx.required {
                    user = userRepository.insert(UserFixture.createRandomValidUser())
                    otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                    otherFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
                    link =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                memo = RandomFixture.randomSentenceWithMax(1000),
                                tags = listOf("first", "second"),
                            ),
                        )
                    otherLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                    firstTodo =
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = link.id!!,
                                ownerId = user.id!!,
                                title = "first-todo",
                                reminderAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
                            ),
                        )
                    secondTodo =
                        todoRepository.insert(
                            TodoFixture
                                .createRandomTodoOf(
                                    linkId = link.id!!,
                                    ownerId = user.id!!,
                                    title = "second-todo",
                                ).complete(RandomFixture.randomDateTime().toInstant().toKotlinInstant()),
                        )
                    otherUserTodo =
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = otherLink.id!!,
                                ownerId = otherUser.id!!,
                            ),
                        )
                    val anotherOwnLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    otherLinkTodo =
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = anotherOwnLink.id!!,
                                ownerId = user.id!!,
                            ),
                        )
                }
            }

            When("memo만 수정하면") {
                val newMemo = RandomFixture.randomSentenceWithMax(1000)
                val request =
                    LinkFixture.createPatchLinkRequest(
                        memo = newMemo,
                    )

                Then("다른 필드는 유지한다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actual = linkRepository.findById(link.id!!)

                    response.memo shouldBe newMemo
                    response.folderId shouldBe link.folderId
                    response.tags shouldBe link.tags
                    response.todos.shouldHaveSize(2)

                    actual shouldNotBe null
                    actual!!.memo shouldBe newMemo
                    actual.folderId shouldBe link.folderId
                    actual.tags shouldBe link.tags
                }
            }

            When("null 요청만 보내면") {
                val request = LinkFixture.createPatchLinkRequest()

                Then("아무 필드도 변경하지 않는다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actual = linkRepository.findById(link.id!!)
                    val actualTodos = todoRepository.findAllByLinkId(link.id!!)

                    response.folderId shouldBe link.folderId
                    response.memo shouldBe link.memo
                    response.tags shouldBe link.tags
                    response.todos.map { it.id } shouldContainExactly actualTodos.map { it.id!! }

                    actual shouldNotBe null
                    actual!!.folderId shouldBe link.folderId
                    actual.memo shouldBe link.memo
                    actual.tags shouldBe link.tags
                }
            }

            When("memo를 빈 문자열로 보내고 folderId를 0으로 보내면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        folderId = 0L,
                        memo = "",
                    )

                Then("둘 다 비운다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actual = linkRepository.findById(link.id!!)

                    response.folderId shouldBe null
                    response.memo shouldBe null

                    actual shouldNotBe null
                    actual!!.folderId shouldBe null
                    actual.memo shouldBe null
                }
            }

            When("tags를 빈 리스트로 보내면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        tags = emptyList(),
                    )

                Then("태그를 모두 삭제한다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actual = linkRepository.findById(link.id!!)

                    response.tags shouldBe emptyList()
                    actual shouldNotBe null
                    actual!!.tags shouldBe emptyList()
                }
            }

            When("todos를 보내지 않으면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        tags = listOf("patched"),
                    )

                Then("기존 todos를 유지한다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actualTodos = todoRepository.findAllByLinkId(link.id!!)

                    response.todos.map { it.id } shouldContainExactly actualTodos.map { it.id!! }
                    actualTodos.map { it.title } shouldContainExactly listOf(firstTodo.title, secondTodo.title)
                }
            }

            When("기존 todo 수정과 신규 todo 생성을 함께 하면") {
                Then("요청에 없는 기존 todo는 삭제하고 완료 상태는 유지한다") {
                    val updatedReminderAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant()
                    val request =
                        LinkFixture.createPatchLinkRequest(
                            todos =
                                listOf(
                                    LinkFixture.createPatchLinkTodoRequest(
                                        id = secondTodo.id,
                                        title = "patched-second",
                                        reminderAt = updatedReminderAt,
                                    ),
                                    LinkFixture.createPatchLinkTodoRequest(
                                        title = "new-todo",
                                        reminderAt = null,
                                    ),
                                ),
                        )
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)
                    val actualTodos = todoRepository.findAllByLinkId(link.id!!)

                    actualTodos.shouldHaveSize(2)
                    actualTodos.map { it.title } shouldContainExactly listOf("patched-second", "new-todo")
                    actualTodos.first { it.id == secondTodo.id!! }.completedAt shouldBe secondTodo.completedAt
                    todoRepository.findById(firstTodo.id!!) shouldBe null

                    response.todos.map { it.title } shouldContainExactly listOf("patched-second", "new-todo")
                }
            }

            When("todos를 빈 리스트로 보내면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        todos = emptyList(),
                    )

                Then("기존 todos를 모두 삭제한다") {
                    val response = patchLinkService.patchLink(user.id!!, link.id!!, request)

                    response.todos shouldBe emptyList()
                    todoRepository.findAllByLinkId(link.id!!) shouldBe emptyList()
                }
            }

            When("todo id가 중복되면") {
                val patch =
                    suspend {
                        val request =
                            LinkFixture.createPatchLinkRequest(
                                todos =
                                    listOf(
                                        LinkFixture.createPatchLinkTodoRequest(id = firstTodo.id, title = "first"),
                                        LinkFixture.createPatchLinkTodoRequest(id = firstTodo.id, title = "second"),
                                    ),
                            )
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DUPLICATE_ID.message) {
                        patch()
                    }
                }
            }

            When("없는 todo id를 보내면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        todos =
                            listOf(
                                LinkFixture.createPatchLinkTodoRequest(
                                    id = RandomFixture.randomId(),
                                    title = "missing",
                                ),
                            ),
                    )
                val patch =
                    suspend {
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_NOT_FOUND.message) {
                        patch()
                    }
                }
            }

            When("다른 사용자 todo id를 보내면") {
                val patch =
                    suspend {
                        val request =
                            LinkFixture.createPatchLinkRequest(
                                todos =
                                    listOf(
                                        LinkFixture.createPatchLinkTodoRequest(
                                            id = otherUserTodo.id,
                                            title = "invalid",
                                        ),
                                    ),
                            )
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_OWNER.message) {
                        patch()
                    }
                }
            }

            When("다른 링크의 본인 todo id를 보내면") {
                val patch =
                    suspend {
                        val request =
                            LinkFixture.createPatchLinkRequest(
                                todos =
                                    listOf(
                                        LinkFixture.createPatchLinkTodoRequest(
                                            id = otherLinkTodo.id,
                                            title = "invalid",
                                        ),
                                    ),
                            )
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_LINK.message) {
                        patch()
                    }
                }
            }

            When("todo가 유효하지 않으면") {
                val patch =
                    suspend {
                        val request =
                            LinkFixture.createPatchLinkRequest(
                                memo = "patched-memo",
                                todos =
                                    listOf(
                                        LinkFixture.createPatchLinkTodoRequest(
                                            id = secondTodo.id,
                                            title = " ",
                                        ),
                                    ),
                            )
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("링크와 todos 모두 수정되지 않는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) {
                        patch()
                    }

                    val actualLink = linkRepository.findById(link.id!!)
                    val actualTodos = todoRepository.findAllByLinkId(link.id!!)

                    actualLink shouldNotBe null
                    actualLink!!.memo shouldBe link.memo
                    actualTodos.map { it.title } shouldContainExactly listOf(firstTodo.title, secondTodo.title)
                }
            }

            When("다른 사용자 폴더를 요청하면") {
                val patch =
                    suspend {
                        val request =
                            LinkFixture.createPatchLinkRequest(
                                folderId = otherFolder.id,
                            )
                        patchLinkService.patchLink(user.id!!, link.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        patch()
                    }
                }
            }

            When("본인 링크가 아니면") {
                val request =
                    LinkFixture.createPatchLinkRequest(
                        memo = "patched",
                    )
                val patch =
                    suspend {
                        patchLinkService.patchLink(user.id!!, otherLink.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        patch()
                    }
                }
            }
        }
    })
