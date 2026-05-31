package com.qlink.ai.service

import com.qlink.ai.client.AiClient
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.client.AiSummaryClientResponse
import com.qlink.ai.client.AiSummaryTodo
import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateLinkAiSummaryServiceTest :
    BaseServiceTest({
        val tx = koinGet<TransactionRunner>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()
        val aiJobRepository = koinGet<AiJobRepository>()
        val dailyUsageRepository = koinGet<DailyUsageRepository>()
        val commandChannel = Channel<Long>(capacity = Channel.BUFFERED)
        val service =
            UpdateLinkAiSummaryService(
                tx = tx,
                userRepository = userRepository,
                folderRepository = folderRepository,
                linkRepository = linkRepository,
                userProviderRepository = userProviderRepository,
                availableModelRepository = availableModelRepository,
                aiJobRepository = aiJobRepository,
                dispatcher = AiSummaryDispatcher(channel = commandChannel),
            )

        suspend fun insertAiContext(
            userId: Long,
            role: UserProviderRole = UserProviderRole.NORMAL,
        ): Pair<UserProvider, AvailableModel> {
            val provider =
                aiProviderRepository.insert(
                    AiProvider(
                        type = AiProviderType.OPENAI,
                        baseUrl = "https://example.com",
                    ),
                )
            val model =
                availableModelRepository.insert(
                    AvailableModel(
                        providerId = provider.id!!,
                        model = RandomFixture.randomSentence(5, 20),
                        priority = 1,
                        rpdLimit = 20,
                        tpdLimit = 2_000_000,
                    ),
                )
            val userProvider =
                userProviderRepository.insert(
                    UserProvider(
                        userId = userId,
                        providerId = provider.id,
                        userRole = role,
                        apiKey = "api-key",
                    ),
                )

            return userProvider to model
        }

        Given("링크 AI 요약 요청 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
            }

            When("기존 링크로 AI 요약 요청을") {
                Then("링크를 생성 중 상태로 바꾸고 작업을 만든다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                            title = "요청 제목",
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }
                    val actualLink = linkRepository.findById(response.id)!!
                    val actualJob = aiJobRepository.findById(jobId)!!

                    response.id shouldBe link.id!!
                    actualLink.status shouldBe LinkStatus.G
                    actualLink.workModelId shouldBe model.id
                    actualJob.linkId shouldBe link.id!!
                    actualJob.userProviderId shouldBe userProvider.id
                    actualJob.requestModelId shouldBe model.id
                    actualJob.requestedUrl shouldBe request.url
                    actualJob.status shouldBe AiJobStatus.P
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
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    withTimeout(1_000) { commandChannel.receive() }
                    val actualLink = linkRepository.findById(response.id)!!
                    val actualJobs = aiJobRepository.findAllByLinkId(response.id)

                    actualLink.status shouldBe LinkStatus.G
                    actualLink.title.startsWith("AI 생성 대기 중 -") shouldBe true
                    actualLink.folderId shouldBe folder.id
                    actualJobs shouldHaveSize 1
                }
            }

            When("worker가 요약 생성에 성공하면") {
                Then("작업, 링크, 사용량, 할 일을 반영한다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }
                    val worker =
                        AiSummaryWorker(
                            tx = tx,
                            aiJobRepository = aiJobRepository,
                            userProviderRepository = userProviderRepository,
                            availableModelRepository = availableModelRepository,
                            aiProviderRepository = aiProviderRepository,
                            dailyUsageRepository = dailyUsageRepository,
                            linkRepository = linkRepository,
                            todoRepository = todoRepository,
                            aiClientRouter =
                                AiClientRouter(
                                    clients = listOf(FakeAiClient()),
                                ),
                            channel = Channel(capacity = Channel.BUFFERED),
                        )

                    worker.proceed(jobId)

                    val actualJob = aiJobRepository.findById(jobId)!!
                    val actualLink = linkRepository.findById(response.id)!!
                    val usage =
                        dailyUsageRepository.findByUserIdAndProviderIdAndUsageDate(
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            usageDate = LocalDate.now(ZoneOffset.UTC),
                        )
                    val todos = todoRepository.findAllByLinkId(response.id)

                    actualJob.status shouldBe AiJobStatus.C
                    actualJob.responseModelId shouldBe model.id
                    actualLink.status shouldBe LinkStatus.A
                    actualLink.title shouldBe "AI 제목"
                    actualLink.summary shouldBe "AI 요약"
                    usage!!.requests shouldBe 1
                    usage.tokens shouldBe 11
                    todos.map { it.title } shouldBe listOf("AI 할 일")
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
        }
    })

private class FakeAiClient : AiClient {
    override val providerType: AiProviderType = AiProviderType.OPENAI

    override suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse =
        AiSummaryClientResponse(
            rawResponse = """{"id":1,"title":"AI 제목","summary":"AI 요약","todos":[{"title":"AI 할 일","reminderAt":null}]}""",
            title = "AI 제목",
            summary = "AI 요약",
            todos = listOf(AiSummaryTodo(title = "AI 할 일", reminderAt = null)),
            usedTokens = 11,
        )
}
