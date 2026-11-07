package com.qlink.ai.service

import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.dto.AiSummaryTodoRequest
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.repository.table.AiProviders
import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.insertAiContext
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.test.TestType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock

class UpdateLinkAiSummaryServiceTest :
    BaseServiceTest({
        val service = koinGet<UpdateLinkAiSummaryService>()
        val urlResolver = koinGet<PreviewImageUrlResolver>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()
        val aiJobRepository = koinGet<AiJobRepository>()
        val commandChannel = koinGet<Channel<Long>>()
        val linkContentRepository = koinGet<LinkContentRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val notificationRepository = koinGet<NotificationRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val tx = koinGet<TransactionRunner>()

        suspend fun insertAiContext(
            userId: Long,
            role: Role = Role.NORMAL,
            type: AiProviderType = AiProviderType.OPENAI,
        ) = insertAiContext(
            userId = userId,
            aiProviderRepository = aiProviderRepository,
            availableModelRepository = availableModelRepository,
            userProviderRepository = userProviderRepository,
            role = role,
            type = type,
        )

        suspend fun <T> withCommittedAiUser(block: suspend (User, UserProvider, AvailableModel) -> T): T =
            CoroutineScope(Dispatchers.IO)
                .async {
                    val owner = tx.required { userRepository.insert(UserFixture.createRandomValidUser()) }
                    val (userProvider, model) = tx.required { insertAiContext(userId = owner.id!!) }
                    try {
                        block(owner, userProvider, model)
                    } finally {
                        tx.required {
                            userRepository.deleteById(owner.id!!)
                            AiProviders.deleteWhere { AiProviders.id eq userProvider.providerId }
                        }
                    }
                }.await()

        suspend fun committedLinkUrlsOf(ownerId: Long): List<String> =
            CoroutineScope(Dispatchers.IO)
                .async { tx.readOnly { linkRepository.findAllUrlsByOwnerId(ownerId) } }
                .await()

        Given("링크 AI 요약 요청 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
            }

            When("기존 링크로 AI 요약 요청을") {
                Then("링크를 생성 중 상태로 바꾸고 작업을 만든다") {
                    val folder = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "요청 사용자 폴더"))
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    val otherFolder =
                        folderRepository.insert(FolderFixture.createFolderWith(ownerId = otherUser.id!!, name = "다른 사용자 폴더"))
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                            title = "요청 제목",
                            thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                            faviconUrl = LinkFixture.validPreviewUrlOf(urlResolver, kind = "favicon"),
                            isVideo = true,
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }
                    val actualLink = linkRepository.findById(response.id)!!
                    val actualJob = aiJobRepository.findById(jobId)!!

                    response.id shouldBe link.id!!
                    actualLink.status shouldBe LinkStatus.G
                    actualLink.thumbnailUrl shouldBe request.thumbnailUrl
                    actualLink.faviconUrl shouldBe request.faviconUrl
                    actualLink.isVideo shouldBe request.isVideo
                    actualLink.workModelId shouldBe model.id
                    actualJob.linkId shouldBe link.id!!
                    actualJob.userProviderId shouldBe userProvider.id
                    actualJob.requestModelId shouldBe model.id
                    actualJob.requestedUrl shouldBe request.url
                    actualJob.prompt.contains("## Fixed Title") shouldBe true
                    actualJob.prompt.contains("- 요청 제목") shouldBe true
                    actualJob.prompt.contains("## Folders") shouldBe true
                    actualJob.prompt.contains("\"id\":null,\"title\":\"미분류\"") shouldBe true
                    actualJob.prompt.contains("\"id\":${folder.id},\"title\":\"${folder.name}\"") shouldBe true
                    actualJob.prompt.contains("\"id\":null,\"title\":\"${folder.name}\"") shouldBe false
                    actualJob.prompt.contains("\"id\":${otherFolder.id},\"title\":\"${otherFolder.name}\"") shouldBe false
                    actualJob.prompt.contains("Do not generate any tasks") shouldBe true
                    actualJob.prompt.contains("add reasonable tasks") shouldBe false
                    actualJob.status shouldBe AiJobStatus.P
                }
            }

            When("generateTodo=true로 AI 요약 요청을") {
                Then("할일 생성 지시가 프롬프트에 포함된다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                            generateTodo = true,
                        )

                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }
                    val actualJob = aiJobRepository.findById(jobId)!!

                    response.id shouldBe link.id!!
                    actualJob.prompt.contains("add reasonable tasks") shouldBe true
                    actualJob.prompt.contains("Do not generate any tasks") shouldBe false
                }
            }

            When("기존 링크의 이미지 값을 생략해 AI 요약을 요청하면") {
                val imageLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                            faviconUrl = LinkFixture.validPreviewUrlOf(urlResolver, kind = "favicon"),
                            isVideo = true,
                        ),
                    )
                val (userProvider, model) = insertAiContext(userId = user.id!!)
                val request =
                    AiSummaryRequest(
                        id = imageLink.id!!,
                        userProviderId = userProvider.id!!,
                        modelId = model.id!!,
                        url = imageLink.url,
                    )
                service.updateLinkAiSummary(user.id!!, request)
                withTimeout(1_000) { commandChannel.receive() }

                Then("저장된 이미지 값은 null, 영상 여부는 false다") {
                    val actual = linkRepository.findById(imageLink.id!!)!!
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe false
                }
            }

            When("신규 링크로 AI 요약 요청을") {
                Then("대기 제목을 가진 링크와 작업을 만든다") {
                    val folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            folderId = folder.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = RandomFixture.randomUrl(),
                            thumbnailUrl = LinkFixture.validPreviewUrlOf(urlResolver),
                            faviconUrl = LinkFixture.validPreviewUrlOf(urlResolver, kind = "favicon"),
                            isVideo = true,
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actualLink = linkRepository.findById(response.id)!!
                    val actualJobs = aiJobRepository.findAllByLinkId(response.id)

                    actualLink.status shouldBe LinkStatus.G
                    actualLink.thumbnailUrl shouldBe request.thumbnailUrl
                    actualLink.faviconUrl shouldBe request.faviconUrl
                    actualLink.isVideo shouldBe request.isVideo
                    actualLink.title.startsWith("AI 생성 대기 중 -") shouldBe true
                    actualLink.folderId shouldBe folder.id
                    actualJobs.first().prompt.contains("## Fixed Title") shouldBe false
                    actualJobs.first().prompt.contains("## Fixed Folder ID") shouldBe true
                    actualJobs.first().prompt.contains("- ${folder.id}") shouldBe true
                    actualJobs shouldHaveSize 1
                    actualLink.memo shouldBe null
                    todoRepository.findAllByLinkId(response.id).shouldBeEmpty()
                }
            }

            When("기본(SUPER_ADMIN) 제공자로 AI 요약 요청을") {
                Then("본인 제공자가 아니어도 작업을 만든다") {
                    val admin = userRepository.insert(UserFixture.createRandomValidUser())
                    val (defaultProvider, model) = insertAiContext(userId = admin.id!!, role = Role.SUPER_ADMIN)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = defaultProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )

                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }
                    val actualJob = aiJobRepository.findById(jobId)!!

                    response.id shouldBe link.id!!
                    actualJob.userProviderId shouldBe defaultProvider.id
                }
            }

            When("본인 것도 기본도 아닌 제공자로 AI 요약 요청을") {
                Then("권한 예외를 반환한다") {
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    val (otherProvider, model) = insertAiContext(userId = otherUser.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = otherProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    val update = suspend { service.updateLinkAiSummary(user.id!!, request) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_USER_PROVIDER_ACCESS_DENIED.message) {
                        update()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                Then("예외를 반환한다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    val update = suspend { service.updateLinkAiSummary(RandomFixture.randomId(), request) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("링크가 없으면") {
                Then("예외를 반환한다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = RandomFixture.randomId(),
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = RandomFixture.randomUrl(),
                        )
                    val update = suspend { service.updateLinkAiSummary(user.id!!, request) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("사용자 제공자 설정이 없으면") {
                Then("예외를 반환한다") {
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = RandomFixture.randomId(),
                            modelId = RandomFixture.randomId(),
                            url = link.url,
                        )
                    val update = suspend { service.updateLinkAiSummary(user.id!!, request) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_USER_PROVIDER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("새 링크로 요청하면") {
                Then("새 링크의 PENDING·INTERACTIVE 행이 있다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = RandomFixture.randomUrl(),
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actual = linkContentRepository.findByLinkId(response.id)

                    actual shouldNotBe null
                    actual!!.status shouldBe ContentStatus.PENDING
                    actual.priority shouldBe ContentPriority.INTERACTIVE
                    actual.sourceUrl shouldBe request.url
                }
            }
        }

        Given("본문 행이 있는 기존 링크와 AI 제공자") {
            lateinit var user: User
            lateinit var link: Link
            lateinit var content: LinkContent

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                content = LinkContentFixture.insertCollected(linkContentRepository, linkId = link.id!!, sourceUrl = link.url)
            }

            When("기존 링크를 다른 URL로 요청하면") {
                Then("행이 같은 id로 새 URL의 PENDING이 되고 본문·발췌가 비어 있다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = RandomFixture.randomUrl(),
                        )
                    service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actual = linkContentRepository.findByLinkId(link.id!!)

                    actual shouldNotBe null
                    actual!!.id shouldBe content.id
                    actual.status shouldBe ContentStatus.PENDING
                    actual.sourceUrl shouldBe request.url
                    actual.page shouldBe null
                    actual.excerpt shouldBe null
                    actual.contentHash shouldBe null
                    actual.collectedAt shouldBe null
                }
            }

            When("기존 링크를 같은 URL로 요청하면") {
                Then("행이 그대로다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actual = linkContentRepository.findByLinkId(link.id!!)

                    actual shouldNotBe null
                    actual!!.id shouldBe content.id
                    actual.status shouldBe ContentStatus.COLLECTED
                    actual.sourceUrl shouldBe link.url
                    actual.excerpt shouldBe content.excerpt
                    actual.nextAttemptAt shouldBe content.nextAttemptAt
                    actual.updatedAt shouldBe content.updatedAt
                }
            }
        }

        Given("가져오기 대기(BULK) 본문 행이 있는 기존 링크와 AI 제공자") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createPendingContentOf(
                            linkId = link.id!!,
                            sourceUrl = link.url,
                            priority = ContentPriority.BULK,
                        ),
                    ),
                )
            }

            When("기존 링크를 다른 URL로 요청하면") {
                Then("행이 새 URL의 INTERACTIVE가 된다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = RandomFixture.randomUrl(),
                        )
                    service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actual = linkContentRepository.findByLinkId(link.id!!)

                    actual shouldNotBe null
                    actual!!.sourceUrl shouldBe request.url
                    actual.priority shouldBe ContentPriority.INTERACTIVE
                }
            }
        }

        Given("다른 사람이 소유한 링크") {
            lateinit var requester: User
            lateinit var otherLink: Link

            beforeTest {
                requester = userRepository.insert(UserFixture.createRandomValidUser())
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                otherLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))
            }

            When("AI 요약을 요청하면") {
                val requesterId = requester.id!!
                val linkId = otherLink.id!!
                val (userProvider, model) = insertAiContext(userId = requesterId)
                val request =
                    AiSummaryRequest(
                        id = linkId,
                        userProviderId = userProvider.id!!,
                        modelId = model.id!!,
                        url = otherLink.url,
                    )
                val update = suspend { service.updateLinkAiSummary(requesterId, request) }

                Then("링크 소유권 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) { update() }
                }
            }
        }

        Given("메모·액션과 함께 새 링크를 요청하는 로그인 사용자") {
            lateinit var user: User
            lateinit var userProvider: UserProvider
            lateinit var model: AvailableModel

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val context = insertAiContext(userId = user.id!!)
                userProvider = context.first
                model = context.second
            }

            fun newLinkRequest(
                memo: String? = null,
                todos: List<AiSummaryTodoRequest>? = null,
                url: String = RandomFixture.randomUrl(),
                folderId: Long? = null,
                userProviderId: Long = userProvider.id!!,
                modelId: Long = model.id!!,
            ): AiSummaryRequest =
                AiSummaryRequest(
                    folderId = folderId,
                    userProviderId = userProviderId,
                    modelId = modelId,
                    url = url,
                    memo = memo,
                    todos = todos,
                )

            suspend fun requestAndDrain(request: AiSummaryRequest): Long {
                val response = service.updateLinkAiSummary(user.id!!, request)
                withTimeout(1_000) { commandChannel.receive() }
                return response.id
            }

            When("todos를 null로 보내면") {
                Then("액션 없이 링크와 AI 작업이 저장된다") {
                    val linkId = requestAndDrain(newLinkRequest(todos = null))

                    linkRepository.findById(linkId)!!.status shouldBe LinkStatus.G
                    aiJobRepository.findAllByLinkId(linkId) shouldHaveSize 1
                    todoRepository.findAllByLinkId(linkId).shouldBeEmpty()
                }
            }

            When("todos를 빈 배열로 보내면") {
                Then("액션 없이 링크와 AI 작업이 저장된다") {
                    val linkId = requestAndDrain(newLinkRequest(todos = emptyList()))

                    linkRepository.findById(linkId)!!.status shouldBe LinkStatus.G
                    aiJobRepository.findAllByLinkId(linkId) shouldHaveSize 1
                    todoRepository.findAllByLinkId(linkId).shouldBeEmpty()
                }
            }

            When("메모와 액션 여러 개를 함께 보내면") {
                Then("링크에 메모가 저장되고 입력한 액션이 모두 그 링크·호출자 소유로 저장되며 본문 행과 AI 작업이 저장된다") {
                    val memo = RandomFixture.randomSentenceWithMax(1000)
                    val todos = List(RandomFixture.randomInt(2, 5)) { AiFixture.createAiSummaryTodoRequest() }
                    val linkId = requestAndDrain(newLinkRequest(memo = memo, todos = todos))
                    val actualTodos = todoRepository.findAllByLinkId(linkId)

                    linkRepository.findById(linkId)!!.memo shouldBe memo
                    actualTodos.map { it.title } shouldContainExactlyInAnyOrder todos.map { it.title }
                    actualTodos.map { it.reminderAt } shouldContainExactlyInAnyOrder todos.map { it.reminderAt }
                    actualTodos.all { it.ownerId == user.id && it.linkId == linkId } shouldBe true
                    linkContentRepository.findByLinkId(linkId) shouldNotBe null
                    aiJobRepository.findAllByLinkId(linkId) shouldHaveSize 1
                }
            }

            When("반복 설정이 있는 액션을 보내면") {
                Then("다음 알림 시각이 계산된 액션이 저장된다") {
                    val todo =
                        AiFixture.createAiSummaryTodoRequest(
                            reminderAt = null,
                            repeatUntil = RandomFixture.randomFutureInstantAfterTomorrow(),
                            repeatDays = RepeatDay.entries,
                            repeatTime = "10:30",
                            repeatTimezone = "Asia/Seoul",
                        )
                    val linkId = requestAndDrain(newLinkRequest(todos = listOf(todo)))
                    val actual = todoRepository.findAllByLinkId(linkId).single()

                    actual.hasRepeat shouldBe true
                    actual.repeatDays shouldContainExactlyInAnyOrder RepeatDay.entries
                    actual.reminderAt shouldNotBe null
                    (actual.reminderAt!! > Clock.System.now()) shouldBe true
                }
            }

            When("링크 URL이 잘못되고 액션도 잘못되면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            user.id!!,
                            newLinkRequest(
                                url = "ftp://${RandomFixture.randomId()}.example.com",
                                todos = listOf(AiFixture.createAiSummaryTodoRequest(title = " ")),
                            ),
                        )
                    }

                Then("링크 입력의 거절이 반환되고 아무것도 저장되지 않는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_NOT_HTTP.message) { update() }
                    linkRepository.findAllUrlsByOwnerId(user.id!!).shouldBeEmpty()
                }
            }

            When("알림 시각이 있는 액션과 없는 액션을 함께 보내면") {
                Then("커밋 뒤 알림 시각이 있는 액션에만 대기 알림이 하나 저장되고 그 문구에 대기 제목이 담긴다") {
                    val withReminder = AiFixture.createAiSummaryTodoRequest()
                    val withoutReminder = AiFixture.createAiSummaryTodoRequest(reminderAt = null)
                    val linkId = requestAndDrain(newLinkRequest(todos = listOf(withReminder, withoutReminder)))
                    val actualLink = linkRepository.findById(linkId)!!
                    val todos = todoRepository.findAllByLinkId(linkId)
                    val reminderTodo = todos.single { it.reminderAt != null }
                    val plainTodo = todos.single { it.reminderAt == null }
                    val pending =
                        notificationRepository.findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = reminderTodo.id!!,
                        )

                    pending shouldHaveSize 1
                    pending.single().willFireAt shouldBe withReminder.reminderAt
                    pending.single().message shouldBe
                        Notification.todo(todo = reminderTodo, linkTitle = actualLink.title, linkUrl = actualLink.url)!!.message
                    actualLink.title.startsWith("AI 생성 대기 중 -") shouldBe true
                    notificationRepository
                        .findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = plainTodo.id!!,
                        ).shouldBeEmpty()
                }
            }

            When("메모와 액션을 함께 보내면") {
                Then("AI 작업 프롬프트에 메모와 액션 제목이 들어가지 않는다") {
                    val memo = RandomFixture.randomSentenceWithMax(1000)
                    val todo = AiFixture.createAiSummaryTodoRequest()
                    val linkId = requestAndDrain(newLinkRequest(memo = memo, todos = listOf(todo)))
                    val prompt = aiJobRepository.findAllByLinkId(linkId).single().prompt

                    prompt.contains(memo) shouldBe false
                    prompt.contains(todo.title) shouldBe false
                }
            }

            When("같은 요청을 두 번 보내면") {
                Then("링크·액션·AI 작업이 두 벌 저장된다") {
                    val request =
                        newLinkRequest(
                            memo = RandomFixture.randomSentenceWithMax(1000),
                            todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                        )
                    val firstId = requestAndDrain(request)
                    val secondId = requestAndDrain(request)

                    firstId shouldNotBe secondId
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldHaveSize 2
                    listOf(firstId, secondId).forEach { linkId ->
                        todoRepository.findAllByLinkId(linkId) shouldHaveSize 1
                        aiJobRepository.findAllByLinkId(linkId) shouldHaveSize 1
                    }
                }
            }

            When("쓰기 권한이 있는 공유 폴더 멤버가 그 폴더로 메모·액션과 함께 요청하면") {
                Then("그 폴더에 링크가 저장되고 액션은 호출자 소유로 저장된다") {
                    val owner = userRepository.insert(UserFixture.createRandomValidUser())
                    val sharedFolder =
                        folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()))
                    folderMemberRepository.insertIfAbsent(
                        FolderMemberFixture.createMember(folderId = sharedFolder.id!!, userId = user.id!!),
                    )
                    val linkId =
                        requestAndDrain(
                            newLinkRequest(
                                folderId = sharedFolder.id,
                                memo = RandomFixture.randomSentenceWithMax(1000),
                                todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                            ),
                        )

                    linkRepository.findById(linkId)!!.folderId shouldBe sharedFolder.id
                    todoRepository.findAllByLinkId(linkId).single().ownerId shouldBe user.id
                }
            }

            When("본인 것도 기본도 아닌 제공자로 메모·액션과 함께 요청하면") {
                val update =
                    suspend {
                        val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                        val (otherProvider, otherModel) = insertAiContext(userId = otherUser.id!!, type = AiProviderType.GEMINI)
                        service.updateLinkAiSummary(
                            user.id!!,
                            newLinkRequest(
                                memo = RandomFixture.randomSentenceWithMax(1000),
                                todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                                userProviderId = otherProvider.id!!,
                                modelId = otherModel.id!!,
                            ),
                        )
                    }

                Then("제공자 거절이 반환되고 링크·액션·AI 작업이 저장되지 않는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_USER_PROVIDER_ACCESS_DENIED.message) { update() }
                    linkRepository.findAllUrlsByOwnerId(user.id!!).shouldBeEmpty()
                }
            }

            When("쓸 수 없는 폴더와 잘못된 액션으로 요청하면") {
                val update =
                    suspend {
                        val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                        val otherFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
                        service.updateLinkAiSummary(
                            user.id!!,
                            newLinkRequest(
                                folderId = otherFolder.id,
                                todos = listOf(AiFixture.createAiSummaryTodoRequest(title = " ")),
                            ),
                        )
                    }

                Then("폴더 거절이 반환된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FOLDER_ACCESS_DENIED.message) { update() }
                }
            }

            When("다른 provider의 모델로 요청하면") {
                val update =
                    suspend {
                        val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                        val (_, otherModel) = insertAiContext(userId = otherUser.id!!, type = AiProviderType.GEMINI)
                        service.updateLinkAiSummary(
                            user.id!!,
                            newLinkRequest(
                                todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                                modelId = otherModel.id!!,
                            ),
                        )
                    }

                Then("모델 없음 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_MODEL_NOT_FOUND.message) { update() }
                }
            }

            When("존재하지 않는 모델과 존재하지 않는 링크 id로 요청하면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            user.id!!,
                            AiSummaryRequest(
                                id = RandomFixture.randomId(),
                                userProviderId = userProvider.id!!,
                                modelId = RandomFixture.randomId(),
                                url = RandomFixture.randomUrl(),
                            ),
                        )
                    }

                Then("모델 없음 오류가 먼저 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_MODEL_NOT_FOUND.message) { update() }
                }
            }
        }

        Given("커밋된 사용자와 AI 제공자로 새 링크를 요청할 때") {
            When("알림 시각이 지난 액션을 보내면") {
                val update =
                    suspend {
                        withCommittedAiUser { owner, committedProvider, committedModel ->
                            val request =
                                AiSummaryRequest(
                                    userProviderId = committedProvider.id!!,
                                    modelId = committedModel.id!!,
                                    url = RandomFixture.randomUrl(),
                                    memo = RandomFixture.randomSentenceWithMax(1000),
                                    todos = listOf(AiFixture.createAiSummaryTodoRequest(reminderAt = RandomFixture.randomPastInstant())),
                                )
                            val result = runCatching { service.updateLinkAiSummary(owner.id!!, request) }
                            result to committedLinkUrlsOf(owner.id!!)
                        }
                    }

                Then("액션 검증 거절이 반환되고 링크·본문 행·액션·AI 작업이 하나도 저장되지 않는다") {
                    val (result, committedUrls) = update()

                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REMINDER_AT_INVALID.message) { result.getOrThrow() }
                    committedUrls.shouldBeEmpty()
                }
            }

            When("첫 액션은 제목이 공백이고 둘째 액션은 반복 시간이 잘못되면") {
                val update =
                    suspend {
                        withCommittedAiUser { owner, committedProvider, committedModel ->
                            val request =
                                AiSummaryRequest(
                                    userProviderId = committedProvider.id!!,
                                    modelId = committedModel.id!!,
                                    url = RandomFixture.randomUrl(),
                                    todos =
                                        listOf(
                                            AiFixture.createAiSummaryTodoRequest(title = " "),
                                            AiFixture.createAiSummaryTodoRequest(
                                                reminderAt = null,
                                                repeatUntil = RandomFixture.randomFutureInstantAfterTomorrow(),
                                                repeatDays = listOf(RepeatDay.MON),
                                                repeatTime = "9:30",
                                            ),
                                        ),
                                )
                            val result = runCatching { service.updateLinkAiSummary(owner.id!!, request) }
                            result to committedLinkUrlsOf(owner.id!!)
                        }
                    }

                Then("첫 액션의 거절이 반환되고 아무것도 저장되지 않는다") {
                    val (result, committedUrls) = update()

                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) { result.getOrThrow() }
                    committedUrls.shouldBeEmpty()
                }
            }
        }

        Given("존재하지 않는 사용자") {
            lateinit var userProvider: UserProvider
            lateinit var model: AvailableModel

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                val providerOwner = userRepository.insert(UserFixture.createRandomValidUser())
                val context = insertAiContext(userId = providerOwner.id!!)
                userProvider = context.first
                model = context.second
            }

            When("id와 메모를 함께 보내면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            RandomFixture.randomId(),
                            AiSummaryRequest(
                                id = RandomFixture.randomId(),
                                userProviderId = userProvider.id!!,
                                modelId = model.id!!,
                                url = RandomFixture.randomUrl(),
                                memo = RandomFixture.randomSentenceWithMax(1000),
                            ),
                        )
                    }

                Then("사용자 확인보다 먼저 기존 링크 내용 거절 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                }
            }

            When("메모·액션과 함께 새 링크를 요청하면") {
                val loginId = RandomFixture.randomId()
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            loginId,
                            AiSummaryRequest(
                                userProviderId = userProvider.id!!,
                                modelId = model.id!!,
                                url = RandomFixture.randomUrl(),
                                memo = RandomFixture.randomSentenceWithMax(1000),
                                todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                            ),
                        )
                    }

                Then("로그인 사용자 없음 오류가 나고 아무것도 저장되지 않는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) { update() }
                    linkRepository.findAllUrlsByOwnerId(loginId).shouldBeEmpty()
                }
            }
        }

        Given("메모와 알림 시각이 있는 액션, 그 대기 알림이 있는 사용자의 기존 링크") {
            lateinit var user: User
            lateinit var link: Link
            lateinit var todo: Todo
            lateinit var notification: Notification
            lateinit var userProvider: UserProvider
            lateinit var model: AvailableModel

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(ownerId = user.id!!, memo = RandomFixture.randomSentenceWithMax(1000)),
                    )
                todo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            reminderAt = RandomFixture.randomFutureInstantAfterTomorrow(),
                        ),
                    )
                notification = notificationRepository.insert(Notification.todo(todo = todo, linkTitle = link.title, linkUrl = link.url)!!)
                val context = insertAiContext(userId = user.id!!)
                userProvider = context.first
                model = context.second
            }

            fun existingLinkRequest(
                memo: String? = null,
                todos: List<AiSummaryTodoRequest>? = null,
                modelId: Long = model.id!!,
            ): AiSummaryRequest =
                AiSummaryRequest(
                    id = link.id!!,
                    userProviderId = userProvider.id!!,
                    modelId = modelId,
                    url = link.url,
                    memo = memo,
                    todos = todos,
                )

            suspend fun assertLinkContentUnchanged() {
                val actual = linkRepository.findById(link.id!!)!!
                val actualTodo = todoRepository.findAllByLinkId(link.id!!).single()
                val pending = notificationRepository.findPendingByContext(context = NotificationContext.TODO, contextId = todo.id!!)

                actual.memo shouldBe link.memo
                actualTodo.id shouldBe todo.id
                actualTodo.title shouldBe todo.title
                actualTodo.reminderAt shouldBe todo.reminderAt
                pending.map { it.id } shouldBe listOf(notification.id)
                pending.single().message shouldBe notification.message
            }

            When("id와 메모를 함께 보내면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(user.id!!, existingLinkRequest(memo = RandomFixture.randomSentenceWithMax(1000)))
                    }

                Then("기존 링크 내용 거절 오류가 나고 링크·액션·대기 알림·AI 작업이 그대로다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                    val actual = linkRepository.findById(link.id!!)!!
                    actual.status shouldBe link.status
                    actual.url shouldBe link.url
                    assertLinkContentUnchanged()
                    aiJobRepository.findAllByLinkId(link.id!!).shouldBeEmpty()
                }
            }

            When("id와 액션을 함께 보내면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            user.id!!,
                            existingLinkRequest(todos = listOf(AiFixture.createAiSummaryTodoRequest())),
                        )
                    }

                Then("기존 링크 내용 거절 오류가 나고 링크·액션·대기 알림·AI 작업이 그대로다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                    linkRepository.findById(link.id!!)!!.status shouldBe link.status
                    assertLinkContentUnchanged()
                    aiJobRepository.findAllByLinkId(link.id!!).shouldBeEmpty()
                }
            }

            When("id와 빈 문자열 메모를 보내면") {
                val update = suspend { service.updateLinkAiSummary(user.id!!, existingLinkRequest(memo = "")) }

                Then("기존 링크 내용 거절 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                }
            }

            When("id와 빈 배열 todos를 보내면") {
                Then("링크가 생성 중이 되고 AI 작업이 만들어지며 기존 메모·액션이 그대로다") {
                    service.updateLinkAiSummary(user.id!!, existingLinkRequest(todos = emptyList()))
                    withTimeout(1_000) { commandChannel.receive() }

                    linkRepository.findById(link.id!!)!!.status shouldBe LinkStatus.G
                    aiJobRepository.findAllByLinkId(link.id!!) shouldHaveSize 1
                    assertLinkContentUnchanged()
                }
            }

            When("id만 보내면") {
                Then("링크가 생성 중이 되고 AI 작업이 만들어지며 기존 메모·액션·대기 알림이 그대로다") {
                    service.updateLinkAiSummary(user.id!!, existingLinkRequest())
                    withTimeout(1_000) { commandChannel.receive() }

                    linkRepository.findById(link.id!!)!!.status shouldBe LinkStatus.G
                    aiJobRepository.findAllByLinkId(link.id!!) shouldHaveSize 1
                    assertLinkContentUnchanged()
                }
            }

            When("id와 함께 다른 provider의 모델로 메모를 보내면") {
                val update =
                    suspend {
                        val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                        val (_, otherModel) = insertAiContext(userId = otherUser.id!!, type = AiProviderType.GEMINI)
                        service.updateLinkAiSummary(
                            user.id!!,
                            existingLinkRequest(memo = RandomFixture.randomSentenceWithMax(1000), modelId = otherModel.id!!),
                        )
                    }

                Then("모델 확인보다 먼저 기존 링크 내용 거절 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                }
            }
        }

        Given("다른 사람이 소유한 메모 없는 링크") {
            lateinit var requester: User
            lateinit var otherLink: Link
            lateinit var userProvider: UserProvider
            lateinit var model: AvailableModel

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                requester = userRepository.insert(UserFixture.createRandomValidUser())
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                otherLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))
                val context = insertAiContext(userId = requester.id!!)
                userProvider = context.first
                model = context.second
            }

            When("id와 메모를 함께 보내면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            requester.id!!,
                            AiSummaryRequest(
                                id = otherLink.id!!,
                                userProviderId = userProvider.id!!,
                                modelId = model.id!!,
                                url = otherLink.url,
                                memo = RandomFixture.randomSentenceWithMax(1000),
                            ),
                        )
                    }

                Then("소유권 확인보다 먼저 기존 링크 내용 거절 오류가 나고 그 링크가 그대로다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                    val actual = linkRepository.findById(otherLink.id!!)!!
                    actual.memo shouldBe null
                    actual.status shouldBe otherLink.status
                }
            }
        }

        Given("존재하지 않는 링크 id") {
            lateinit var user: User
            lateinit var userProvider: UserProvider
            lateinit var model: AvailableModel

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val context = insertAiContext(userId = user.id!!)
                userProvider = context.first
                model = context.second
            }

            When("id와 액션을 함께 보내면") {
                val update =
                    suspend {
                        service.updateLinkAiSummary(
                            user.id!!,
                            AiSummaryRequest(
                                id = RandomFixture.randomId(),
                                userProviderId = userProvider.id!!,
                                modelId = model.id!!,
                                url = RandomFixture.randomUrl(),
                                todos = listOf(AiFixture.createAiSummaryTodoRequest()),
                            ),
                        )
                    }

                Then("링크 확인보다 먼저 기존 링크 내용 거절 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED.message) { update() }
                }
            }
        }
    })
