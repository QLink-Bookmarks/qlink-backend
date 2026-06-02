package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.SourceType
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkTodoRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.link.service.CreateLinkService
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinInstant

class CreateLinkServiceTest :
    BaseServiceTest({
        val createLinkService = koinGet<CreateLinkService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("링크 생성 서비스 테스트") {
            lateinit var user: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("링크 생성을") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val expected = createLinkService.createLink(user.id!!, request)

                Then("성공한다") {
                    val actual = linkRepository.findById(expected.id)
                    val expectedTags = request.tags.distinct()

                    actual shouldNotBe null
                    actual!!.id shouldBe expected.id
                    actual.url shouldBe request.url
                    actual.title shouldBe request.title
                    actual.summary shouldBe request.summary
                    actual.memo shouldBe request.memo
                    actual.sourceType shouldBe request.sourceType
                    actual.thumbnailUrl shouldBe request.thumbnailUrl
                    actual.tags shouldBe expectedTags
                }
            }

            When("todos를 포함한 링크 생성을") {
                val ignoredReminderAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val repeatUntil = Clock.System.now().plus(30.days)
                val todos =
                    listOf(
                        CreateLinkTodoRequest(
                            title = RandomFixture.randomSentenceWithMax(50),
                            reminderAt = ignoredReminderAt,
                            repeatUntil = repeatUntil,
                            repeatDays = listOf(RepeatDay.MONDAY, RepeatDay.FRIDAY),
                            repeatTime = "20:10",
                            repeatTimezone = "Asia/Seoul",
                        ),
                        CreateLinkTodoRequest(
                            title = RandomFixture.randomSentenceWithMax(50),
                            reminderAt = null,
                        ),
                    )
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                        todos = todos,
                    )
                val expected = createLinkService.createLink(user.id!!, request)

                Then("링크와 todos를 함께 저장한다") {
                    val actualLink = linkRepository.findById(expected.id)
                    val actualTodos = todoRepository.findAllByLinkIdForLinkDetail(expected.id)

                    actualLink shouldNotBe null
                    actualTodos.map { it.title } shouldContainExactly todos.map { it.title }
                    actualTodos.first().reminderAt shouldNotBe ignoredReminderAt
                    actualTodos.first().repeatUntil shouldBe repeatUntil
                    actualTodos.first().repeatDays shouldBe listOf(RepeatDay.MONDAY, RepeatDay.FRIDAY)
                    actualTodos.first().repeatTime shouldBe "20:10"
                    actualTodos.last().reminderAt shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(invalidUserId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("폴더 아이디가 요청에 포함됐지만 폴더가 없으면") {
                val invalidFolderId = RandomFixture.randomId()
                val request =
                    CreateLinkRequest(
                        folderId = invalidFolderId,
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FOLDER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("todo가 유효하지 않으면") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                        todos =
                            listOf(
                                CreateLinkTodoRequest(
                                    title = " ",
                                    reminderAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
                                ),
                            ),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("todo 생성 시 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) {
                        create()
                    }
                }
            }
        }
    })
