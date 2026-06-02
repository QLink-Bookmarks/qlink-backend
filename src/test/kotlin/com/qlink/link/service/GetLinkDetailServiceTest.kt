package com.qlink.link.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalTime
import kotlin.time.toKotlinInstant

class GetLinkDetailServiceTest :
    BaseServiceTest({
        val getLinkDetailService = koinGet<GetLinkDetailService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()

        suspend fun modelFixture(): AvailableModel {
            val provider =
                AiFixture
                    .createRandomValidAiProvider(type = AiProviderType.CLAUDE)
                    .let { aiProvider -> aiProviderRepository.findByType(aiProvider.type) ?: aiProviderRepository.insert(aiProvider) }

            return availableModelRepository
                .findAllByProviderId(provider.id!!)
                .firstOrNull()
                ?: availableModelRepository.insert(AiFixture.createRandomAvailableModelOf(providerId = provider.id))
        }

        Given("링크 상세 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("폴더와 할 일이 있는 본인 링크 상세 조회를") {
                val model = modelFixture()
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            folderId = folder.id,
                            workModelId = model.id,
                        ),
                    )
                val otherLink =
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                val todos =
                    listOf(
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = link.id!!,
                                ownerId = user.id!!,
                                repeatUntil =
                                    RandomFixture
                                        .futureDateTime(30, java.util.concurrent.TimeUnit.DAYS)
                                        .toInstant()
                                        .toKotlinInstant(),
                                repeatDays = listOf(RepeatDay.FRIDAY),
                                repeatTime = LocalTime.of(7, 30),
                                repeatTimezone = "Asia/Seoul",
                            ),
                        ),
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = link.id,
                                ownerId = user.id!!,
                                completedAt =
                                    RandomFixture
                                        .randomDateTime()
                                        .toInstant()
                                        .toKotlinInstant(),
                            ),
                        ),
                    )
                todoRepository.insert(
                    TodoFixture.createRandomTodoOf(
                        linkId = otherLink.id!!,
                        ownerId = user.id!!,
                    ),
                )
                val expectedFolderId = folder.id
                val expectedFolderName = folder.name
                val expectedFolderEmoji = folder.emoji
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id)

                Then("성공한다") {
                    actual.id shouldBe link.id
                    actual.url shouldBe link.url
                    actual.title shouldBe link.title
                    actual.summary shouldBe link.summary
                    actual.tags shouldBe link.tags
                    actual.memo shouldBe link.memo
                    actual.sourceType shouldBe link.sourceType
                    actual.createdAt shouldBe link.createdAt
                    actual.folderId shouldBe expectedFolderId
                    actual.folderName shouldBe expectedFolderName
                    actual.folderEmoji shouldBe expectedFolderEmoji
                    actual.workModel shouldBe model.model
                    actual.todos shouldContainExactly todos.map { it.toExpectedTodo() }
                }
            }

            When("폴더가 없는 본인 링크 상세 조회를") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id!!)

                Then("폴더 정보 없이 성공한다") {
                    actual.id shouldBe link.id
                    actual.folderId shouldBe null
                    actual.folderName shouldBe null
                    actual.folderEmoji shouldBe null
                    actual.workModel shouldBe null
                    actual.todos shouldBe emptyList()
                }
            }

            When("없는 링크 상세 조회를") {
                val invalidLinkId = RandomFixture.randomId()
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, invalidLinkId)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        getDetail()
                    }
                }
            }

            When("다른 사용자의 링크 상세 조회를") {
                val link: Link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = otherUser.id!!,
                        ),
                    )
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, link.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        getDetail()
                    }
                }
            }
        }
    })

private fun Todo.toExpectedTodo() =
    LinkDetailTodoQuery(
        id = id!!,
        title = title,
        completedAt = completedAt,
        reminderAt = reminderAt,
        repeatUntil = repeatUntil,
        repeatDays = repeatDays,
        repeatTime = repeatTime?.toString(),
    )
