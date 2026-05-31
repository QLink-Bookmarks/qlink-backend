package com.qlink.ai.worker

import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.service.UpdateLinkAiSummaryService
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.FakeAiClient
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.insertAiContext
import com.qlink.support.koinGet
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneOffset

class AiSummaryWorkerTest :
    BaseServiceTest({
        val service = koinGet<UpdateLinkAiSummaryService>()
        val worker = koinGet<AiSummaryWorker>()
        val commandChannel = koinGet<Channel<Long>>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()
        val aiJobRepository = koinGet<AiJobRepository>()
        val dailyUsageRepository = koinGet<DailyUsageRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val fakeAiClient = koinGet<FakeAiClient>()

        suspend fun insertAiContext(userId: Long) =
            insertAiContext(
                userId = userId,
                aiProviderRepository = aiProviderRepository,
                availableModelRepository = availableModelRepository,
                userProviderRepository = userProviderRepository,
            )

        Given("AI 요약 worker 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                fakeAiClient.reset()
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
            }

            When("요약 생성 작업을 처리하면") {
                Then("작업, 링크, 사용량, 할 일을 반영한다") {
                    val folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    fakeAiClient.usedTokens = 831
                    fakeAiClient.folderId = folder.id
                    fakeAiClient.tags = listOf("포털", "검색")
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }

                    worker.proceed(jobId)

                    val actualJob = aiJobRepository.findById(jobId)!!
                    val actualLink = linkRepository.findById(response.id)!!
                    val usage =
                        dailyUsageRepository.findByUserIdAndProviderIdAndUsageDate(
                            userProviderId = userProvider.id,
                            modelId = model.id,
                            usageDate = LocalDate.now(ZoneOffset.UTC),
                        )
                    val todos = todoRepository.findAllByLinkId(response.id)

                    actualJob.status shouldBe AiJobStatus.C
                    actualJob.responseModelId shouldBe model.id
                    actualLink.status shouldBe LinkStatus.A
                    actualLink.folderId shouldBe folder.id
                    actualLink.title shouldBe "AI 제목"
                    actualLink.summary shouldBe "AI 요약"
                    actualLink.tags shouldBe listOf("포털", "검색")
                    usage!!.requests shouldBe 1
                    usage.tokens shouldBe 831
                    todos.map { it.title } shouldBe listOf("AI 할 일")
                }
            }

            When("AI 응답 linkId가 작업 링크와 다르면") {
                Then("작업과 링크를 실패 처리한다") {
                    val (userProvider, model) = insertAiContext(userId = user.id!!)
                    fakeAiClient.linkId = link.id!! + 1
                    val request =
                        AiSummaryRequest(
                            id = link.id!!,
                            userProviderId = userProvider.id!!,
                            modelId = model.id!!,
                            url = link.url,
                        )
                    val response = service.updateLinkAiSummary(user.id!!, request)
                    val jobId = withTimeout(1_000) { commandChannel.receive() }

                    worker.proceed(jobId)

                    val actualJob = aiJobRepository.findById(jobId)!!
                    val actualLink = linkRepository.findById(response.id)!!

                    actualJob.status shouldBe AiJobStatus.F
                    actualLink.status shouldBe LinkStatus.F
                }
            }
        }
    })
