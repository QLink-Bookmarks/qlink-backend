package com.qlink.folder.service

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.folderinvite.repository.table.FolderInvites
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
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
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant

class DeleteFolderServiceTest :
    BaseServiceTest({
        val service = koinGet<DeleteFolderService>()
        val folders = koinGet<FolderRepository>()
        val members = koinGet<FolderMemberRepository>()
        val links = koinGet<LinkRepository>()
        val todos = koinGet<TodoRepository>()
        val notifications = koinGet<NotificationRepository>()
        val jobs = koinGet<AiJobRepository>()
        val users = koinGet<UserRepository>()
        val providers = koinGet<AiProviderRepository>()
        val models = koinGet<AvailableModelRepository>()
        val userProviders = koinGet<UserProviderRepository>()
        val usages = koinGet<DailyUsageRepository>()

        suspend fun insertAiJobsAndUsageFor(
            ownerId: Long,
            sourceLinks: List<Link>,
        ): PersistedAiState {
            val candidateProvider = AiFixture.createRandomValidAiProvider()
            val provider = providers.findByType(candidateProvider.type) ?: providers.insert(candidateProvider)
            val model = models.insert(AiFixture.createRandomAvailableModelOf(provider.id!!))
            val userProvider =
                userProviders.insert(
                    UserProvider(
                        userId = ownerId,
                        providerId = provider.id,
                        apiKey = RandomFixture.randomSentenceWithMax(100),
                    ),
                )
            val persistedUsage =
                usages.insert(
                    DailyUsage(
                        userProviderId = userProvider.id!!,
                        modelId = model.id!!,
                        usageDate = LocalDate.now(),
                        requests = RandomFixture.randomInt(1, 50),
                        tokens = RandomFixture.randomInt(1, 1000),
                    ),
                )
            val persistedJobs =
                sourceLinks.map { link ->
                    jobs.insert(
                        AiJob(
                            linkId = link.id!!,
                            userProviderId = userProvider.id,
                            requestModelId = model.id,
                            requestedUrl = link.url,
                            prompt = RandomFixture.randomSentenceWithMax(1000),
                        ),
                    )
                }
            return PersistedAiState(jobs = persistedJobs, usage = persistedUsage)
        }

        suspend fun verifyLinkPreserved(
            original: Link,
            folderId: Long?,
        ) {
            val actual = links.findById(original.id!!).shouldNotBeNull()
            actual.folderId shouldBe folderId
            actual.ownerId shouldBe original.ownerId
            actual.url shouldBe original.url
            actual.title shouldBe original.title
            actual.summary shouldBe original.summary
            actual.memo shouldBe original.memo
            actual.tags shouldBe original.tags
            actual.thumbnailUrl shouldBe original.thumbnailUrl
            actual.faviconUrl shouldBe original.faviconUrl
            actual.isVideo shouldBe original.isVideo
            actual.sourceType shouldBe original.sourceType
            actual.status shouldBe original.status
            actual.workModelId shouldBe original.workModelId
            actual.favoriteAt shouldBe original.favoriteAt
            actual.createdAt shouldBe original.createdAt
            actual.updatedAt!! shouldBeGreaterThan original.updatedAt!!
        }

        val cases =
            listOf(
                DeletionCase(
                    shared = false,
                    option = null,
                    linksPerOwner = 3,
                    title = "개인 source에서 onDelete/target을 생략하면",
                ),
                DeletionCase(
                    shared = true,
                    option = null,
                    linksPerOwner = 3,
                    title = "shared source에서 onDelete/target을 생략하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "NuLl",
                    linksPerOwner = 3,
                    title = "문자열 null 옵션으로 실행하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "MoVe",
                    linksPerOwner = 3,
                    title = "move를 실행하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "cascade",
                    linksPerOwner = 3,
                    title = "개인 source에서 cascade를 실행하면",
                ),
                DeletionCase(
                    shared = true,
                    option = "CaScAdE",
                    linksPerOwner = 3,
                    title = "pending 이외 fired/failed TODO 이력과 무관한 링크·공지 알림이 함께 있을 때 cascade하면",
                ),
                DeletionCase(
                    shared = false,
                    option = null,
                    linksPerOwner = 0,
                    title = "빈 source를 null로 처리하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "move",
                    linksPerOwner = 0,
                    title = "빈 개인 source를 유효 target으로 move하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "cascade",
                    linksPerOwner = 0,
                    title = "빈 source를 cascade로 처리하면",
                ),
                DeletionCase(
                    shared = false,
                    option = null,
                    linksPerOwner = 1,
                    title = "링크 하나인 source를 null로 처리하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "move",
                    linksPerOwner = 1,
                    title = "내 링크 하나인 개인 source를 move하면",
                ),
                DeletionCase(
                    shared = false,
                    option = "cascade",
                    linksPerOwner = 1,
                    title = "내 링크 하나인 source를 cascade로 처리하면",
                ),
            )
        cases.forEach { case ->
            val shared = case.shared
            val option = case.option
            val count = case.linksPerOwner
            val context =
                when {
                    count == 0 -> "링크가 없는 source와 무관한 링크·알림"
                    count == 1 -> "내 링크 하나인 개인 source와 무관한 링크·알림"
                    shared -> "여러 멤버의 링크·액션·AI job·예약 알림이 있는 shared source"
                    else -> "호출자·타인 링크와 각각의 자식·pending/fired/failed 알림이 있는 non-shared source"
                }
            Given("$context — ${case.title}") {
                lateinit var owner: User
                lateinit var other: User
                lateinit var source: Folder
                lateinit var target: Folder
                lateinit var sourceLinks: List<Link>
                lateinit var sourceTodos: List<Todo>
                lateinit var sourceJobs: List<AiJob>
                lateinit var pending: List<Notification>
                lateinit var history: List<Notification>
                lateinit var unrelated: Link
                lateinit var unrelatedPending: Notification
                lateinit var usage: DailyUsage
                var inviteId = 0L

                beforeTest {
                    owner = users.insert(UserFixture.createRandomValidUser())
                    other = users.insert(UserFixture.createRandomValidUser())
                    source =
                        folders.insert(
                            FolderFixture.createFolderWith(
                                ownerId = owner.id!!,
                                sharedAt = if (shared) RandomFixture.randomPastInstant() else null,
                            ),
                        )
                    target = folders.insert(FolderFixture.createValidUnsharedFolder(owner.id!!))
                    members.insertIfAbsent(FolderMemberFixture.createOwnerMember(folderId = source.id!!, userId = owner.id!!))
                    members.insertIfAbsent(FolderMemberFixture.createMember(folderId = source.id!!, userId = other.id!!))
                    inviteId =
                        FolderInvites.insert {
                            it[folderId] = source.id!!
                            it[inviterId] = owner.id!!
                            it[token] = "invite-${RandomFixture.randomId()}"
                            it[expiresAt] = RandomFixture.randomPastInstant().toJavaInstant()
                        }[FolderInvites.id]
                    sourceLinks =
                        (if (count == 1) listOf(owner) else listOf(owner, other)).flatMap { user ->
                            List(count) {
                                links.insert(
                                    LinkFixture.createRandomLinkOf(
                                        ownerId = user.id!!,
                                        folderId = source.id,
                                        createdAt = RandomFixture.randomPastInstant(),
                                    ),
                                )
                            }
                        }
                    sourceTodos =
                        sourceLinks.map {
                            todos.insert(TodoFixture.createRandomTodoOf(linkId = it.id!!, ownerId = it.ownerId))
                        }
                    val aiState = insertAiJobsAndUsageFor(ownerId = owner.id!!, sourceLinks = sourceLinks)
                    sourceJobs = aiState.jobs
                    usage = aiState.usage
                    // Every todo has recipients other than its owner too; deletion is context based.
                    pending =
                        sourceTodos.flatMap { todo ->
                            listOf(owner, other).map { user ->
                                notifications.insert(
                                    NotificationFixture.createRandomNotificationOf(userId = user.id!!, contextId = todo.id!!),
                                )
                            }
                        }
                    history =
                        sourceTodos.flatMap { todo ->
                            listOf(
                                NotificationFixture.createTodoNotificationOf(
                                    todo = todo,
                                    willFireAt = todo.reminderAt!! - 1.days,
                                    firedAt = RandomFixture.randomPastInstant(),
                                ),
                                NotificationFixture.createTodoNotificationOf(
                                    todo = todo,
                                    willFireAt = todo.reminderAt!! - 2.days,
                                    failedAt = RandomFixture.randomPastInstant(),
                                ),
                                NotificationFixture.createRandomNotificationOf(
                                    userId = owner.id!!,
                                    context = NotificationContext.ANNOUNCE,
                                    contextId = todo.id!!,
                                ),
                            ).map { notifications.insert(it) }
                        }
                    unrelated = links.insert(LinkFixture.createRandomLinkOf(ownerId = other.id!!, folderId = target.id))
                    val unrelatedTodo =
                        todos.insert(
                            TodoFixture.createRandomTodoOf(linkId = unrelated.id!!, ownerId = other.id!!),
                        )
                    unrelatedPending = notifications.insert(NotificationFixture.createTodoNotificationOf(unrelatedTodo))
                }

                When(case.title) {
                    val delete =
                        suspend {
                            service.deleteFolder(
                                loginId = owner.id!!,
                                folderId = source.id!!,
                                onDelete = option,
                                targetFolderId = target.id.takeIf { option.equals("move", ignoreCase = true) },
                            )
                        }
                    Then("처리 집합만 변경하고 보존 대상과 이력은 유지한다") {
                        shouldNotThrow<BusinessException> { delete() }
                        folders.findById(source.id!!) shouldBe null
                        members.findAllByFolderIdOrderByJoinedAtAsc(source.id!!) shouldBe emptyList()
                        FolderInvites.selectAll().where { FolderInvites.id eq inviteId }.count() shouldBe 0
                        sourceLinks.forEachIndexed { index, link ->
                            val deleted = option.equals("cascade", ignoreCase = true) && (shared || link.ownerId == owner.id)
                            if (deleted) {
                                links.findById(link.id!!) shouldBe null
                                todos.findById(sourceTodos[index].id!!) shouldBe null
                                jobs.findById(sourceJobs[index].id!!) shouldBe null
                            } else {
                                val destination =
                                    target.id.takeIf {
                                        option.equals("move", ignoreCase = true) && link.ownerId == owner.id
                                    }
                                verifyLinkPreserved(original = link, folderId = destination)
                                todos.findById(sourceTodos[index].id!!)?.title shouldBe sourceTodos[index].title
                                jobs.findById(sourceJobs[index].id!!)?.prompt shouldBe sourceJobs[index].prompt
                            }
                            pending.filter { it.contextId == sourceTodos[index].id }.forEach {
                                (notifications.findById(it.id!!) == null) shouldBe deleted
                            }
                        }
                        history.forEach { notifications.findById(it.id!!)?.context shouldBe it.context }
                        links.findById(unrelated.id!!)?.folderId shouldBe target.id
                        notifications.findById(unrelatedPending.id!!).shouldNotBeNull()
                        val movedCount = if (option.equals("move", ignoreCase = true)) count else 0
                        links.findAllByFolderIds(listOf(target.id!!)).size shouldBe 1 + movedCount
                        val savedUsage =
                            usages
                                .findByUserIdAndProviderIdAndUsageDate(
                                    userProviderId = usage.userProviderId,
                                    modelId = usage.modelId,
                                    usageDate = usage.usageDate,
                                ).shouldNotBeNull()
                        savedUsage.requests shouldBe usage.requests
                        savedUsage.tokens shouldBe usage.tokens
                        val model = models.findById(usage.modelId).shouldNotBeNull()
                        providers.findById(model.providerId).shouldNotBeNull()
                    }
                }
            }
        }

        Given("타인 링크만 남은 개인 source와 현재 쓰기 가능한 공유 target") {
            lateinit var owner: User
            lateinit var other: User
            lateinit var source: Folder
            lateinit var target: Folder
            lateinit var link: Link
            beforeTest {
                owner = users.insert(UserFixture.createRandomValidUser())
                other = users.insert(UserFixture.createRandomValidUser())
                source = folders.insert(FolderFixture.createValidUnsharedFolder(owner.id!!))
                target = folders.insert(FolderFixture.createSharedFolderOf(other.id!!))
                members.insertIfAbsent(FolderMemberFixture.createMember(folderId = target.id!!, userId = owner.id!!))
                link =
                    links.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = other.id!!,
                            folderId = source.id,
                            createdAt = RandomFixture.randomPastInstant(),
                        ),
                    )
            }
            listOf("move", "cascade").forEach { option ->
                When("내 링크 없이 $option 처리하면") {
                    val delete =
                        suspend {
                            service.deleteFolder(
                                loginId = owner.id!!,
                                folderId = source.id!!,
                                onDelete = option,
                                targetFolderId = target.id.takeIf { option == "move" },
                            )
                        }
                    Then("타인 링크는 미분류로 남고 target은 늘지 않는다") {
                        shouldNotThrow<BusinessException> { delete() }
                        verifyLinkPreserved(original = link, folderId = null)
                        links.findAllByFolderIds(listOf(target.id!!)) shouldBe emptyList()
                        folders.findById(source.id!!) shouldBe null
                    }
                }
            }
            When("내 링크를 쓰기 가능한 공유 target으로 옮기면") {
                lateinit var ownLink: Link
                beforeTest {
                    ownLink =
                        links.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = owner.id!!,
                                folderId = source.id,
                                createdAt = RandomFixture.randomPastInstant(),
                            ),
                        )
                }
                val delete =
                    suspend {
                        service.deleteFolder(
                            loginId = owner.id!!,
                            folderId = source.id!!,
                            onDelete = "move",
                            targetFolderId = target.id,
                        )
                    }
                Then("같은 내 링크가 소유권을 유지한 채 공유 target에 저장된다") {
                    shouldNotThrow<BusinessException> { delete() }
                    verifyLinkPreserved(original = ownLink, folderId = target.id)
                    verifyLinkPreserved(original = link, folderId = null)
                }
            }
        }

        Given("존재하는 source와 링크·멤버·pending 알림") {
            lateinit var owner: User
            lateinit var source: Folder
            lateinit var link: Link
            lateinit var pending: Notification
            beforeTest {
                owner = users.insert(UserFixture.createRandomValidUser())
                source = folders.insert(FolderFixture.createValidUnsharedFolder(owner.id!!))
                link = links.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = source.id))
                members.insertIfAbsent(FolderMemberFixture.createOwnerMember(folderId = source.id!!, userId = owner.id!!))
                val todo = todos.insert(TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = owner.id!!))
                pending = notifications.insert(NotificationFixture.createTodoNotificationOf(todo))
            }
            listOf(0L, RandomFixture.randomId()).forEach { targetId ->
                When("존재하지 않는 target $targetId 로 move하면") {
                    val delete =
                        suspend {
                            service.deleteFolder(
                                loginId = owner.id!!,
                                folderId = source.id!!,
                                onDelete = "move",
                                targetFolderId = targetId,
                            )
                        }
                    Then("TARGET_NOT_FOUND이고 source와 연관 데이터가 그대로다") {
                        shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_NOT_FOUND.message) { delete() }
                        folders.findById(source.id!!)?.ownerId shouldBe owner.id
                        links.findById(link.id!!)?.folderId shouldBe source.id
                        members.findByFolderIdAndUserId(folderId = source.id!!, userId = owner.id!!).shouldNotBeNull()
                        notifications.findById(pending.id!!).shouldNotBeNull()
                    }
                }
            }
            When("호출자가 없으면") {
                val delete = suspend { service.deleteFolder(loginId = RandomFixture.randomId(), folderId = source.id!!, onDelete = null) }
                Then("OWNER_NOT_FOUND이고 source와 링크가 남는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) { delete() }
                    folders.findById(source.id!!).shouldNotBeNull()
                    links.findById(link.id!!)?.folderId shouldBe source.id
                }
            }
            When("없는 source를 옵션과 target 없이 삭제하면") {
                val delete =
                    suspend {
                        service.deleteFolder(loginId = owner.id!!, folderId = RandomFixture.randomId(), onDelete = null)
                    }
                Then("추가 정리 없이 무관한 데이터가 유지된다") {
                    shouldNotThrow<BusinessException> { delete() }
                    links.findById(link.id!!)?.folderId shouldBe source.id
                    notifications.findById(pending.id!!).shouldNotBeNull()
                }
            }
            listOf(null, "unknown", "move").forEach { option ->
                When("source가 없는 상태에서 option=$option 과 잘못된 target을 제공하면") {
                    val delete =
                        suspend {
                            service.deleteFolder(
                                loginId = owner.id!!,
                                folderId = RandomFixture.randomId(),
                                onDelete = option,
                                targetFolderId = 0,
                            )
                        }
                    Then("업무 검사 없이 성공하고 무관한 데이터가 유지된다") {
                        shouldNotThrow<BusinessException> { delete() }
                        links.findById(link.id!!)?.folderId shouldBe source.id
                        notifications.findById(pending.id!!).shouldNotBeNull()
                    }
                }
            }
            listOf(null, "move", "cascade").forEach { option ->
                When("$option 삭제를 성공한 뒤 같은 요청을 다시 실행하면") {
                    lateinit var target: Folder
                    beforeTest { target = folders.insert(FolderFixture.createValidUnsharedFolder(owner.id!!)) }
                    val delete =
                        suspend {
                            service.deleteFolder(
                                loginId = owner.id!!,
                                folderId = source.id!!,
                                onDelete = option,
                                targetFolderId = target.id.takeIf { option == "move" },
                            )
                        }
                    Then("첫 저장 결과에서 추가 변경이 없다") {
                        shouldNotThrow<BusinessException> { delete() }
                        val saved = links.findById(link.id!!)
                        val pendingAfter = notifications.findById(pending.id!!)
                        shouldNotThrow<BusinessException> { delete() }
                        folders.findById(source.id!!) shouldBe null
                        links.findById(link.id!!)?.folderId shouldBe saved?.folderId
                        links.findById(link.id!!)?.updatedAt shouldBe saved?.updatedAt
                        links.findById(link.id!!)?.ownerId shouldBe saved?.ownerId
                        val targetLinkIds = links.findAllByFolderIds(listOf(target.id!!)).map { it.id }
                        targetLinkIds shouldBe if (option == "move") listOf(link.id) else emptyList()
                        notifications.findById(pending.id!!)?.id shouldBe pendingAfter?.id
                    }
                }
            }
        }
    }) {
    private data class DeletionCase(
        val shared: Boolean,
        val option: String?,
        val linksPerOwner: Int,
        val title: String,
    )

    private data class PersistedAiState(
        val jobs: List<AiJob>,
        val usage: DailyUsage,
    )
}
