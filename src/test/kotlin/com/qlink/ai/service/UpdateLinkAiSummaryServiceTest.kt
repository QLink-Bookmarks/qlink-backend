package com.qlink.ai.service

import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.insertAiContext
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

class UpdateLinkAiSummaryServiceTest :
    BaseServiceTest({
        val service = koinGet<UpdateLinkAiSummaryService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()
        val aiJobRepository = koinGet<AiJobRepository>()
        val commandChannel = koinGet<Channel<Long>>()

        suspend fun insertAiContext(userId: Long) =
            insertAiContext(
                userId = userId,
                aiProviderRepository = aiProviderRepository,
                availableModelRepository = availableModelRepository,
                userProviderRepository = userProviderRepository,
            )

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
                    actualJob.prompt.contains("## Fixed Title") shouldBe true
                    actualJob.prompt.contains("- 요청 제목") shouldBe true
                    actualJob.prompt.contains("## Folders") shouldBe true
                    actualJob.prompt.contains("\"id\":null,\"title\":\"미분류\"") shouldBe true
                    actualJob.prompt.contains("\"id\":${folder.id},\"title\":\"${folder.name}\"") shouldBe true
                    actualJob.prompt.contains("\"id\":null,\"title\":\"${folder.name}\"") shouldBe false
                    actualJob.prompt.contains("\"id\":${otherFolder.id},\"title\":\"${otherFolder.name}\"") shouldBe false
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
                    actualJobs.first().prompt.contains("## Fixed Title") shouldBe false
                    actualJobs.first().prompt.contains("## Fixed Folder ID") shouldBe true
                    actualJobs.first().prompt.contains("- ${folder.id}") shouldBe true
                    actualJobs shouldHaveSize 1
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
