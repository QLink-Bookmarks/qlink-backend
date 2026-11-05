package com.qlink.ai.service

import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.dto.AiSummaryCompletion
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.repository.table.AiJobs
import com.qlink.common.error.BusinessException
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.link.repository.table.Links
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.NotificationFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.insertAiContext
import com.qlink.support.koinGet
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.test.TestType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock

class CompleteAiSummaryServiceTest :
    BaseServiceTest({
        val service = koinGet<CompleteAiSummaryService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val memberRepository = koinGet<FolderMemberRepository>()
        val jobRepository = koinGet<AiJobRepository>()
        val usageRepository = koinGet<DailyUsageRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val notificationRepository = koinGet<NotificationRepository>()
        val resolver = koinGet<PreviewImageUrlResolver>()

        Given("진행 중인 AI 작업과 작업 시작 snapshot") {
            lateinit var source: Folder
            lateinit var candidate: Folder
            lateinit var link: Link
            lateinit var unrelatedLink: Link
            lateinit var unrelatedNotification: Notification
            lateinit var completion: AiSummaryCompletion
            lateinit var initialUsage: DailyUsage

            beforeTest {
                if (it.type != TestType.Test) return@beforeTest
                val user = userRepository.insert(UserFixture.createRandomValidUser())
                source = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                candidate = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
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
                            folderId = source.id,
                            status = LinkStatus.G,
                            workModelId = model.id,
                            thumbnailUrl = LinkFixture.validPreviewUrlOf(resolver),
                        ),
                    )
                val job =
                    jobRepository.insert(
                        AiFixture.createPendingAiJobOf(link = link, userProviderId = provider.id!!, modelId = model.id!!),
                    )
                completion = AiFixture.createAiSummaryCompletionOf(job = job, link = link, candidateFolderId = candidate.id)
                initialUsage =
                    usageRepository.insert(
                        DailyUsage(
                            userProviderId = provider.id!!,
                            modelId = model.id!!,
                            usageDate = LocalDate.now(ZoneOffset.UTC),
                            requests = RandomFixture.randomInt(1, 10),
                            tokens = RandomFixture.randomInt(1, 1000),
                        ),
                    )
                unrelatedLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                unrelatedNotification = notificationRepository.insert(NotificationFixture.createRandomNotificationOf(user.id!!))
            }

            suspend fun currentUsage(): DailyUsage =
                usageRepository.findByUserIdAndProviderIdAndUsageDate(
                    userProviderId = initialUsage.userProviderId,
                    modelId = initialUsage.modelId,
                    usageDate = initialUsage.usageDate,
                )!!

            suspend fun assertCompletedIn(folderId: Long?) {
                val actual = linkRepository.findById(link.id!!)!!
                actual.id shouldBe link.id
                actual.ownerId shouldBe link.ownerId
                actual.url shouldBe link.url
                actual.folderId shouldBe folderId
                actual.title shouldBe completion.response.title
                actual.summary shouldBe completion.response.summary
                actual.tags shouldBe completion.response.tags
                actual.memo shouldBe link.memo
                actual.thumbnailUrl shouldBe link.thumbnailUrl
                actual.status shouldBe LinkStatus.A
                actual.workModelId shouldBe completion.responseModelId
                searchTextOf(link.id!!) shouldBe
                    listOf(
                        completion.response.title,
                        link.url,
                        completion.response.tags
                            .distinct()
                            .joinToString(" "),
                        completion.response.summary,
                        link.memo.orEmpty(),
                    ).joinToString(" ")
                val job = jobRepository.findById(completion.snapshot.jobId)!!
                job.status shouldBe AiJobStatus.C
                job.response shouldBe completion.response.rawResponse
                val todos = todoRepository.findAllByLinkId(link.id!!)
                todos.map { it.title } shouldBe completion.response.todos.map { it.title }
                val todo = todos.single()
                val pending =
                    notificationRepository.findPendingByContext(context = NotificationContext.TODO, contextId = todo.id!!)
                pending.shouldHaveSize(1)
                pending.single().userId shouldBe link.ownerId
                pending.single().willFireAt shouldBe todo.reminderAt
                val usage = currentUsage()
                usage.requests shouldBe initialUsage.requests + 1
                usage.tokens shouldBe initialUsage.tokens + completion.response.usedTokens
                linkRepository.findById(unrelatedLink.id!!)!!.title shouldBe unrelatedLink.title
                notificationRepository.findById(unrelatedNotification.id!!)!!.willFireAt shouldBe unrelatedNotification.willFireAt
            }

            When("시작 revision과 현재 revision이 같고 후보가 유효하면") {
                val complete = suspend { service.complete(completion) }
                Then("작업과 링크 AI 결과, 후보 배정, 액션, 예약과 사용량을 저장한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(candidate.id)
                }
            }

            When("링크와 작업이 먼저 cascade 삭제되면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    linkRepository.deleteById(link.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("삭제 데이터를 재생성하지 않고 기존 사용량과 무관한 데이터도 보존한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    linkRepository.findById(link.id!!) shouldBe null
                    jobRepository.findById(completion.snapshot.jobId) shouldBe null
                    todoRepository.findAllByLinkId(link.id!!) shouldHaveSize 0
                    currentUsage().requests shouldBe initialUsage.requests
                    currentUsage().tokens shouldBe initialUsage.tokens
                    linkRepository.findById(unrelatedLink.id!!)!!.title shouldBe unrelatedLink.title
                    notificationRepository.findById(unrelatedNotification.id!!) shouldNotBe null
                }
            }

            When("링크는 있지만 작업만 먼저 삭제되면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    // There is no standalone job-delete repository operation; arrange the callback's missing job directly.
                    AiJobs.deleteWhere { AiJobs.id eq completion.snapshot.jobId }
                }
                val complete = suspend { service.complete(completion) }
                Then("링크 내용과 배정, 액션, 사용량이 변하지 않는다") {
                    shouldNotThrow<BusinessException> { complete() }
                    val actual = linkRepository.findById(link.id!!)!!
                    actual.folderId shouldBe link.folderId
                    actual.title shouldBe link.title
                    actual.summary shouldBe link.summary
                    actual.status shouldBe link.status
                    todoRepository.findAllByLinkId(link.id!!) shouldHaveSize 0
                    currentUsage().requests shouldBe initialUsage.requests
                    currentUsage().tokens shouldBe initialUsage.tokens
                }
            }

            When("source 삭제로 다른 target으로 이동한 뒤 완료하면") {
                lateinit var target: Folder
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    target = folderRepository.insert(FolderFixture.createValidUnsharedFolder(link.ownerId))
                    linkRepository.assignFolderByIds(linkIds = listOf(link.id!!), folderId = target.id, now = Clock.System.now())
                    folderRepository.deleteById(source.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("최신 target과 id, owner를 보존하고 AI 결과를 저장한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(target.id)
                }
            }

            When("source가 detach 삭제된 뒤 완료하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    folderRepository.deleteById(source.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("미분류 배정과 증가한 revision을 보존한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(null)
                    linkRepository.findById(link.id!!)!!.folderAssignmentRevision shouldBeGreaterThan
                        completion.snapshot.folderAssignmentRevision
                }
            }

            When("시작 A에서 B로 이동한 후 B 삭제로 다시 A로 이동하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    val intermediate = folderRepository.insert(FolderFixture.createValidUnsharedFolder(link.ownerId))
                    linkRepository.assignFolderByIds(linkIds = listOf(link.id!!), folderId = intermediate.id, now = Clock.System.now())
                    linkRepository.assignFolderByIds(linkIds = listOf(link.id!!), folderId = source.id, now = Clock.System.now())
                    folderRepository.deleteById(intermediate.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("revision 증가를 확인하고 AI 후보가 A를 덮어쓰지 않는다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(source.id)
                    linkRepository.findById(link.id!!)!!.folderAssignmentRevision shouldBeGreaterThan
                        completion.snapshot.folderAssignmentRevision
                }
            }

            When("배정은 그대로지만 AI 후보 폴더를 먼저 삭제하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    folderRepository.deleteById(candidate.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("현재 유효 배정을 유지하고 삭제된 후보를 저장하지 않는다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(source.id)
                }
            }

            When("시작 시 고정 폴더였던 source가 먼저 삭제되면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    completion = completion.copy(candidateFolderId = source.id)
                    folderRepository.deleteById(source.id!!)
                }
                val complete = suspend { service.complete(completion) }
                Then("삭제된 고정 폴더를 되살리지 않고 null을 유지한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(null)
                }
            }

            When("공유 후보의 현재 쓰기 가능한 멤버이면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    candidate = folderRepository.insert(FolderFixture.createSharedFolderOf(otherUser.id!!))
                    memberRepository.insertIfAbsent(FolderMemberFixture.createMember(folderId = candidate.id!!, userId = link.ownerId))
                    completion = completion.copy(candidateFolderId = candidate.id)
                }
                val complete = suspend { service.complete(completion) }
                Then("공유 후보 폴더를 적용하고 AI 결과를 저장한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(candidate.id)
                }
            }

            When("시작 revision 그대로 AI가 미분류를 선택하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    completion = completion.copy(candidateFolderId = null)
                }
                val complete = suspend { service.complete(completion) }
                Then("기존 재요약처럼 미분류로 옮기고 AI 결과를 저장한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(null)
                }
            }

            When("시작 이후 이동한 상태에서 AI가 미분류를 선택하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    linkRepository.assignFolderByIds(linkIds = listOf(link.id!!), folderId = candidate.id, now = Clock.System.now())
                    completion = completion.copy(candidateFolderId = null)
                }
                val complete = suspend { service.complete(completion) }
                Then("최신 이동 배정을 보존하고 AI 결과를 저장한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(candidate.id)
                }
            }

            When("공유 후보의 현재 member가 먼저 제거되면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    candidate = folderRepository.insert(FolderFixture.createSharedFolderOf(otherUser.id!!))
                    memberRepository.insertIfAbsent(FolderMemberFixture.createMember(folderId = candidate.id!!, userId = link.ownerId))
                    completion = completion.copy(candidateFolderId = candidate.id)
                    memberRepository.deleteByFolderIdAndUserId(folderId = candidate.id!!, userId = link.ownerId)
                }
                val complete = suspend { service.complete(completion) }
                Then("후보를 새 배정으로 쓰지 않고 최신 배정을 보존한다") {
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(source.id)
                }
            }

            When("배정과 무관한 내용만 먼저 변경하면") {
                beforeTest {
                    if (it.type != TestType.Test) return@beforeTest
                    linkRepository.update(
                        link.update(
                            folderId = link.folderId,
                            url = link.url,
                            title = RandomFixture.randomSentenceWithMax(100),
                            summary = link.summary,
                            memo = link.memo,
                            tags = link.tags,
                            thumbnailUrl = link.thumbnailUrl,
                            sourceType = link.sourceType,
                            previewPublicBaseUrl = resolver.publicBaseUrl,
                            previewEnvironment = resolver.environment,
                        ),
                    )
                }
                val complete = suspend { service.complete(completion) }
                Then("내용 변경을 이동으로 오인하지 않고 유효 후보를 적용한다") {
                    linkRepository.findById(link.id!!)!!.folderAssignmentRevision shouldBe completion.snapshot.folderAssignmentRevision
                    shouldNotThrow<BusinessException> { complete() }
                    assertCompletedIn(candidate.id)
                }
            }
        }
    })

private fun searchTextOf(linkId: Long): String =
    Links
        .select(Links.searchText)
        .where { Links.id eq linkId }
        .single()[Links.searchText]
