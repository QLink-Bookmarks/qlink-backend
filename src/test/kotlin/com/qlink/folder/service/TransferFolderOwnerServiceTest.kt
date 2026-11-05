package com.qlink.folder.service

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.folderinvite.repository.table.FolderInvites
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.Notification
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.toJavaInstant

class TransferFolderOwnerServiceTest :
    BaseServiceTest({
        val service = koinGet<TransferFolderOwnerService>()
        val jobs = koinGet<AiJobRepository>()
        val providers = koinGet<AiProviderRepository>()
        val models = koinGet<AvailableModelRepository>()
        val userProviders = koinGet<UserProviderRepository>()
        val users = koinGet<UserRepository>()
        val folders = koinGet<FolderRepository>()
        val members = koinGet<FolderMemberRepository>()
        val links = koinGet<LinkRepository>()
        val todos = koinGet<TodoRepository>()
        val notifications = koinGet<NotificationRepository>()

        suspend fun insertAiJobsFor(
            ownerId: Long,
            sourceLinks: List<Link>,
        ): List<AiJob> {
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
            return sourceLinks.map { link ->
                jobs.insert(
                    AiJob(
                        linkId = link.id!!,
                        userProviderId = userProvider.id!!,
                        requestModelId = model.id!!,
                        requestedUrl = link.url,
                        prompt = RandomFixture.randomSentenceWithMax(1000),
                    ),
                )
            }
        }

        Given("owner·대상·다른 멤버와 각자의 링크·액션·초대가 있는 공유 폴더") {
            lateinit var owner: User
            lateinit var target: User
            lateinit var other: User
            lateinit var folder: Folder
            lateinit var savedLinks: List<Link>
            lateinit var savedTodos: List<Todo>
            lateinit var savedJobs: List<AiJob>
            lateinit var savedNotifications: List<Notification>
            var inviteId = 0L
            var inviteToken = ""
            beforeTest {
                owner = users.insert(UserFixture.createRandomValidUser())
                target = users.insert(UserFixture.createRandomValidUser())
                other = users.insert(UserFixture.createRandomValidUser())
                folder = folders.insert(FolderFixture.createSharedFolderOf(owner.id!!))
                members.insertIfAbsent(FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = owner.id!!))
                listOf(target, other).forEach {
                    members.insertIfAbsent(FolderMemberFixture.createMember(folderId = folder.id!!, userId = it.id!!))
                }
                savedLinks =
                    listOf(owner, target, other).map {
                        links.insert(LinkFixture.createRandomLinkOf(ownerId = it.id!!, folderId = folder.id))
                    }
                savedTodos = savedLinks.map { todos.insert(TodoFixture.createRandomTodoOf(linkId = it.id!!, ownerId = it.ownerId)) }
                savedNotifications = savedTodos.map { notifications.insert(NotificationFixture.createTodoNotificationOf(it)) }
                savedJobs = insertAiJobsFor(ownerId = owner.id!!, sourceLinks = savedLinks)
                inviteToken = "invite-${RandomFixture.randomId()}"
                inviteId =
                    FolderInvites.insert {
                        it[folderId] = folder.id!!
                        it[inviterId] = owner.id!!
                        it[token] = inviteToken
                        it[expiresAt] = RandomFixture.randomPastInstant().toJavaInstant()
                    }[FolderInvites.id]
            }

            suspend fun verifyUnchangedContent() {
                savedLinks.forEach { original ->
                    val actual = links.findById(original.id!!).shouldNotBeNull()
                    actual.ownerId shouldBe original.ownerId
                    actual.folderId shouldBe original.folderId
                    actual.title shouldBe original.title
                    actual.url shouldBe original.url
                    actual.summary shouldBe original.summary
                    actual.memo shouldBe original.memo
                    actual.tags shouldBe original.tags
                    actual.status shouldBe original.status
                    actual.updatedAt shouldBe original.updatedAt
                }
                savedTodos.forEach { todos.findById(it.id!!)?.title shouldBe it.title }
                savedJobs.forEach { jobs.findById(it.id!!)?.prompt shouldBe it.prompt }
                savedNotifications.forEach {
                    notifications.findById(it.id!!)?.willFireAt shouldBe it.willFireAt
                    notifications.findByContext(context = it.context, contextId = it.contextId).map { row -> row.id } shouldBe listOf(it.id)
                }
                listOf(owner, target, other).forEach { user ->
                    notifications.countUnread(user.id!!) shouldBe savedNotifications.count { it.userId == user.id && it.firedAt != null }
                }
                FolderInvites.selectAll().where { FolderInvites.id eq inviteId }.single()[FolderInvites.token] shouldBe inviteToken
            }

            suspend fun verifyTransferred() {
                folders.findById(folder.id!!)?.ownerId shouldBe target.id
                members.findByFolderIdAndUserId(folderId = folder.id!!, userId = target.id!!)?.role shouldBe MemberRole.OWNER
                members.findByFolderIdAndUserId(folderId = folder.id!!, userId = owner.id!!) shouldBe null
                members.findByFolderIdAndUserId(folderId = folder.id!!, userId = other.id!!)?.role shouldBe MemberRole.MEMBER
                verifyUnchangedContent()
            }
            When("현재 멤버에게 위임하면") {
                val transfer = suspend { service.transferOwner(loginId = owner.id!!, folderId = folder.id!!, ownerId = target.id!!) }
                Then("owner·OWNER 승격·기존 owner 탈퇴가 저장되고 링크·액션·초대·알림은 유지된다") {
                    shouldNotThrow<BusinessException> { transfer() }
                    verifyTransferred()
                }
            }
            When("원래 owner member 행이 없는 레거시 폴더에서 위임하면") {
                beforeTest { members.deleteByFolderIdAndUserId(folderId = folder.id!!, userId = owner.id!!) }
                val transfer = suspend { service.transferOwner(loginId = owner.id!!, folderId = folder.id!!, ownerId = target.id!!) }
                Then("소유자와 대상 역할이 바뀌고 caller 행은 계속 없다") {
                    shouldNotThrow<BusinessException> { transfer() }
                    verifyTransferred()
                }
            }
            When("대상 member가 없으면") {
                beforeTest { members.deleteByFolderIdAndUserId(folderId = folder.id!!, userId = target.id!!) }
                val transfer = suspend { service.transferOwner(loginId = owner.id!!, folderId = folder.id!!, ownerId = target.id!!) }
                Then("MEMBER_NOT_FOUND이며 원래 상태가 유지된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_MEMBER_NOT_FOUND.message) { transfer() }
                    folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                    members.findByFolderIdAndUserId(folderId = folder.id!!, userId = owner.id!!)?.role shouldBe MemberRole.OWNER
                    members.findByFolderIdAndUserId(folderId = folder.id!!, userId = target.id!!) shouldBe null
                    verifyUnchangedContent()
                }
            }
            listOf(true, false).forEach { duplicate ->
                When("대상이 ${if (duplicate) "동명" else "다른 이름"} 폴더를 소유하면") {
                    lateinit var existing: Folder
                    beforeTest {
                        existing =
                            folders.insert(
                                FolderFixture.createFolderWith(
                                    ownerId = target.id!!,
                                    name = if (duplicate) folder.name else RandomFixture.randomSentenceWithMax(100),
                                ),
                            )
                    }
                    val transfer = suspend { service.transferOwner(loginId = owner.id!!, folderId = folder.id!!, ownerId = target.id!!) }
                    Then(if (duplicate) "DUPLICATE_NAME이며 어느 폴더·멤버도 바뀌지 않는다" else "위임되고 기존 대상 폴더는 보존된다") {
                        if (duplicate) {
                            shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_DUPLICATE_NAME.message) { transfer() }
                            folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                            members.findByFolderIdAndUserId(folderId = folder.id!!, userId = target.id!!)?.role shouldBe MemberRole.MEMBER
                            members.findByFolderIdAndUserId(folderId = folder.id!!, userId = owner.id!!)?.role shouldBe MemberRole.OWNER
                            verifyUnchangedContent()
                        } else {
                            shouldNotThrow<BusinessException> { transfer() }
                            verifyTransferred()
                        }
                        folders.findById(existing.id!!)?.name shouldBe existing.name
                        folders.findById(existing.id!!)?.ownerId shouldBe target.id
                    }
                }
            }
            listOf(false, true).forEach { missingSource ->
                When("caller가 없고 source 부재=$missingSource 이면") {
                    val transfer =
                        suspend {
                            service.transferOwner(
                                loginId = RandomFixture.randomId(),
                                folderId = if (missingSource) RandomFixture.randomId() else folder.id!!,
                                ownerId = target.id!!,
                            )
                        }
                    Then("CALLER_NOT_FOUND가 먼저 발생하고 데이터는 유지된다") {
                        shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_CALLER_NOT_FOUND.message) { transfer() }
                        folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                        verifyUnchangedContent()
                    }
                }
            }
            When("caller는 있지만 source가 없으면") {
                val transfer =
                    suspend { service.transferOwner(loginId = owner.id!!, folderId = RandomFixture.randomId(), ownerId = target.id!!) }
                Then("FOLDER_NOT_FOUND이고 기존 폴더는 유지된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_FOLDER_NOT_FOUND.message) { transfer() }
                    folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                }
            }
        }
    })
