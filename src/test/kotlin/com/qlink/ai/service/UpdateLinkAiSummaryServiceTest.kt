package com.qlink.ai.service

import com.qlink.ai.client.AiProvider
import com.qlink.ai.worker.AiSummaryCommand
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

class UpdateLinkAiSummaryServiceTest :
    BaseServiceTest({
        val tx = koinGet<TransactionRunner>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val commandChannel = Channel<AiSummaryCommand>(capacity = Channel.BUFFERED)
        val updateLinkAiSummaryService =
            UpdateLinkAiSummaryService(
                tx = tx,
                linkRepository = linkRepository,
                dispatcher = AiSummaryDispatcher(channel = commandChannel),
            )

        Given("링크 AI 요약 요청 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
            }

            When("AI 요약 요청을") {
                Then("큐에 등록한다") {
                    val loginId = user.id!!
                    val linkId = link.id!!
                    val request =
                        AiSummaryRequest(
                            linkId = linkId,
                            provider = AiProvider.OPENAI,
                        )
                    val response = updateLinkAiSummaryService.updateLinkAiSummary(loginId, request)
                    val command = withTimeout(1_000) { commandChannel.receive() }

                    response.linkId shouldBe request.linkId
                    response.status shouldBe AiSummaryStatus.QUEUED
                    command.ownerId shouldBe loginId
                    command.linkId shouldBe request.linkId
                    command.provider shouldBe request.provider
                }
            }

            When("링크가 없으면") {
                val request =
                    AiSummaryRequest(
                        linkId = RandomFixture.randomId(),
                        provider = AiProvider.GEMINI,
                    )
                val update =
                    suspend {
                        updateLinkAiSummaryService.updateLinkAiSummary(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        update()
                    }
                }
            }
        }
    })
