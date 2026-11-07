package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkTodoRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.link.service.CreateLinkService
import com.qlink.link.worker.LinkPreviewFillSignal
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.drainLinkIds
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.support.truncatedToSecond
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.test.TestType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinInstant

class CreateLinkServiceTest :
    BaseServiceTest({
        val createLinkService = koinGet<CreateLinkService>()
        val urlResolver = koinGet<PreviewImageUrlResolver>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val notificationRepository = koinGet<NotificationRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()
        val previewFillSignal = koinGet<LinkPreviewFillSignal>()
        val tx = koinGet<TransactionRunner>()

        suspend fun <T> withCommittedUser(block: suspend (User) -> T): T =
            CoroutineScope(Dispatchers.IO)
                .async {
                    val owner = tx.required { userRepository.insert(UserFixture.createRandomValidUser()) }
                    try {
                        block(owner)
                    } finally {
                        tx.required { userRepository.deleteById(owner.id!!) }
                    }
                }.await()

        suspend fun committedLinkUrlsOf(ownerId: Long): List<String> =
            CoroutineScope(Dispatchers.IO)
                .async { tx.readOnly { linkRepository.findAllUrlsByOwnerId(ownerId) } }
                .await()

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
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                        faviconUrl = LinkFixture.validPreviewUrlOf(urlResolver, kind = "favicon"),
                        isVideo = true,
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
                    actual.faviconUrl shouldBe request.faviconUrl
                    actual.isVideo shouldBe request.isVideo
                    actual.tags shouldBe expectedTags
                }
            }

            When("링크를 만들면") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        sourceType = LinkFixture.randomRequestSourceType(),
                        tags = RandomFixture.randomSentenceList(),
                    )

                Then("그 링크의 본문 행이 PENDING·INTERACTIVE로 1개 있다") {
                    val created = createLinkService.createLink(user.id!!, request)
                    val content = linkContentRepository.findByLinkId(created.id)

                    content shouldNotBe null
                    content!!.status shouldBe ContentStatus.PENDING
                    content.priority shouldBe ContentPriority.INTERACTIVE
                    content.sourceUrl shouldBe request.url
                    content.attempt shouldBe 0
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
                            repeatDays = listOf(RepeatDay.MON, RepeatDay.FRI),
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
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
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
                    actualTodos.first().repeatUntil.truncatedToSecond() shouldBe repeatUntil.truncatedToSecond()
                    actualTodos.first().repeatDays shouldBe listOf(RepeatDay.MON, RepeatDay.FRI)
                    actualTodos.first().repeatTime shouldBe "20:10"
                    actualTodos.last().reminderAt shouldBe null

                    val notification =
                        notificationRepository
                            .findPendingByContext(
                                context = NotificationContext.TODO,
                                contextId = actualTodos.first().id,
                            ).single()
                    notification.willFireAt shouldBe actualTodos.first().reminderAt
                    notification.willFireAt shouldNotBe ignoredReminderAt
                    notificationRepository
                        .findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = actualTodos.last().id,
                        ).size shouldBe 0
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
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
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
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
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

            When("공유 폴더의 멤버가 해당 폴더로 링크 생성을") {
                Then("성공한다") {
                    val owner = userRepository.insert(UserFixture.createRandomValidUser())
                    val sharedFolder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()),
                        )
                    folderMemberRepository.insertIfAbsent(
                        FolderMemberFixture.createMember(folderId = sharedFolder.id!!, userId = user.id!!),
                    )
                    val request =
                        CreateLinkRequest(
                            folderId = sharedFolder.id,
                            url = RandomFixture.randomUrl(),
                            title = RandomFixture.randomSentenceWithMax(300),
                            sourceType = LinkFixture.randomRequestSourceType(),
                            tags = RandomFixture.randomSentenceList(),
                        )

                    val response = createLinkService.createLink(user.id!!, request)
                    val createdLink = linkRepository.findById(response.id)

                    createdLink shouldNotBe null
                    createdLink!!.ownerId shouldBe user.id
                    createdLink.folderId shouldBe sharedFolder.id
                }
            }

            When("공유 폴더의 멤버가 아닌 사용자가 해당 폴더로 링크 생성을") {
                Then("예외를 반환한다") {
                    val owner = userRepository.insert(UserFixture.createRandomValidUser())
                    val sharedFolder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()),
                        )
                    val request =
                        CreateLinkRequest(
                            folderId = sharedFolder.id,
                            url = RandomFixture.randomUrl(),
                            title = RandomFixture.randomSentenceWithMax(300),
                            sourceType = LinkFixture.randomRequestSourceType(),
                            tags = RandomFixture.randomSentenceList(),
                        )

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FOLDER_ACCESS_DENIED.message) {
                        createLinkService.createLink(user.id!!, request)
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
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                        tags = RandomFixture.randomSentenceList(),
                        todos =
                            listOf(
                                CreateLinkTodoRequest(
                                    title = " ",
                                    reminderAt = RandomFixture.futureDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant(),
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

        Given("로그인한 사용자") {
            lateinit var user: User

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                previewFillSignal.drainLinkIds()
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("썸네일·파비콘 없이 링크를 만들면") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        sourceType = LinkFixture.randomRequestSourceType(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("링크가 둘 다 비어 저장되고 채우기 시그널에 그 링크 id가 하나 들어온다") {
                    val created = create()
                    val actual = linkRepository.findById(created.id)

                    actual shouldNotBe null
                    actual!!.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    previewFillSignal.drainLinkIds() shouldContainExactly listOf(created.id)
                }
            }

            When("썸네일만 보내 링크를 만들면") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        sourceType = LinkFixture.randomRequestSourceType(),
                        thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("채우기 시그널에 id가 들어오지 않는다") {
                    create()

                    previewFillSignal.drainLinkIds().shouldBeEmpty()
                }
            }

            When("파비콘만 보내 링크를 만들면") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        sourceType = LinkFixture.randomRequestSourceType(),
                        faviconUrl = LinkFixture.validPreviewUrlOf(urlResolver, kind = "favicon"),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("채우기 시그널에 id가 들어오지 않는다") {
                    create()

                    previewFillSignal.drainLinkIds().shouldBeEmpty()
                }
            }
        }

        Given("커밋된 로그인한 사용자") {
            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                previewFillSignal.drainLinkIds()
            }

            When("썸네일·파비콘 없이 보냈지만 트랜잭션 안에서 실패하면") {
                val create =
                    suspend {
                        withCommittedUser { owner ->
                            val request =
                                CreateLinkRequest(
                                    url = RandomFixture.randomUrl(),
                                    title = RandomFixture.randomSentenceWithMax(300),
                                    sourceType = LinkFixture.randomRequestSourceType(),
                                    tags = RandomFixture.randomSentenceList(),
                                    todos = listOf(CreateLinkTodoRequest(title = " ")),
                                )
                            val result = runCatching { createLinkService.createLink(owner.id!!, request) }
                            result to committedLinkUrlsOf(owner.id!!)
                        }
                    }

                Then("링크가 저장되지 않고 채우기 시그널에 id가 들어오지 않는다") {
                    val (result, committedUrls) = create()

                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) { result.getOrThrow() }
                    committedUrls.shouldBeEmpty()
                    previewFillSignal.drainLinkIds().shouldBeEmpty()
                }
            }
        }
    })
