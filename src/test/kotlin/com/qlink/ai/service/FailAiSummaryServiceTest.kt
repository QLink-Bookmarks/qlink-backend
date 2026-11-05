package com.qlink.ai.service

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.link.repository.table.Links
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.insertAiContext
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.test.TestType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock

class FailAiSummaryServiceTest :
    BaseServiceTest({
        val service = koinGet<FailAiSummaryService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val jobRepository = koinGet<AiJobRepository>()

        Given("진행 중인 요약 작업과 링크") {
            lateinit var link: Link
            lateinit var job: AiJob

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                val user = userRepository.insert(UserFixture.createRandomValidUser())
                val folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                val (provider, model) =
                    insertAiContext(
                        userId = user.id!!,
                        aiProviderRepository = koinGet<AiProviderRepository>(),
                        availableModelRepository = koinGet<AvailableModelRepository>(),
                        userProviderRepository = koinGet<UserProviderRepository>(),
                    )
                link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            folderId = folder.id,
                            status = LinkStatus.G,
                            workModelId = model.id,
                        ),
                    )
                job =
                    jobRepository.insert(
                        AiFixture.createPendingAiJobOf(link = link, userProviderId = provider.id!!, modelId = model.id!!),
                    )
            }

            When("실패를 저장하면") {
                val reason = RandomFixture.randomSentenceWithMax(200)
                val fail = suspend { service.fail(jobId = job.id!!, reason = reason, now = Clock.System.now()) }

                Then("작업이 실패 상태가 되고 링크의 제목·요약·상태만 바뀌며 links.url·folder_id는 그대로다") {
                    shouldNotThrow<BusinessException> { fail() }
                    val actualJob = jobRepository.findById(job.id!!)!!
                    val actual = linkRepository.findById(link.id!!)!!

                    actualJob.status shouldBe AiJobStatus.F
                    actual.title shouldStartWith "AI 생성 실패 - "
                    actual.summary shouldBe reason
                    actual.status shouldBe LinkStatus.F
                    actual.url shouldBe link.url
                    actual.folderId shouldBe link.folderId
                    actual.memo shouldBe link.memo
                    actual.tags shouldBe link.tags
                    actual.workModelId shouldBe link.workModelId
                    searchTextOf(link.id!!) shouldBe
                        listOf(actual.title, link.url, link.tags.joinToString(" "), reason, link.memo.orEmpty()).joinToString(" ")
                }
            }

            When("작업이 없으면") {
                val fail = suspend { service.fail(jobId = RandomFixture.randomId(), reason = "reason", now = Clock.System.now()) }

                Then("아무것도 바꾸지 않고 끝난다") {
                    shouldNotThrow<BusinessException> { fail() }
                    val actualJob = jobRepository.findById(job.id!!)!!
                    val actual = linkRepository.findById(link.id!!)!!

                    actualJob.status shouldBe AiJobStatus.P
                    actual.title shouldBe link.title
                    actual.summary shouldBe link.summary
                    actual.status shouldBe LinkStatus.G
                }
            }

            When("링크가 삭제돼 작업도 cascade로 없으면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    linkRepository.deleteById(link.id!!)
                }

                val fail = suspend { service.fail(jobId = job.id!!, reason = "reason", now = Clock.System.now()) }

                Then("아무것도 바꾸지 않고 끝난다") {
                    shouldNotThrow<BusinessException> { fail() }

                    linkRepository.findById(link.id!!) shouldBe null
                    jobRepository.findById(job.id!!) shouldBe null
                }
            }
        }
    })

private fun searchTextOf(linkId: Long): String =
    Links
        .select(Links.searchText)
        .where { Links.id eq linkId }
        .single()[Links.searchText]
