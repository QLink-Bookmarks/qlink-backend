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

class DeleteFolderMemberServiceTest :
    BaseServiceTest({
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
        val service = koinGet<DeleteFolderMemberService>()

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

        Given("공유 폴더 owner·member·다른 멤버와 각자의 링크·자식") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var other: User
            lateinit var folder: Folder
            lateinit var savedLinks: List<Link>
            lateinit var savedTodos: List<Todo>
            lateinit var savedJobs: List<AiJob>
            lateinit var savedNotifications: List<Notification>
            beforeTest {
                owner = users.insert(UserFixture.createRandomValidUser())
                member = users.insert(UserFixture.createRandomValidUser())
                other = users.insert(UserFixture.createRandomValidUser())
                folder = folders.insert(FolderFixture.createSharedFolderOf(owner.id!!))
                members.insertIfAbsent(FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = owner.id!!))
                listOf(member, other).forEach {
                    members.insertIfAbsent(FolderMemberFixture.createMember(folderId = folder.id!!, userId = it.id!!))
                }
                savedLinks =
                    listOf(
                        owner,
                        member,
                        other,
                    ).map { links.insert(LinkFixture.createRandomLinkOf(ownerId = it.id!!, folderId = folder.id)) }
                savedTodos = savedLinks.map { todos.insert(TodoFixture.createRandomTodoOf(linkId = it.id!!, ownerId = it.ownerId)) }
                savedNotifications = savedTodos.map { notifications.insert(NotificationFixture.createTodoNotificationOf(it)) }
                savedJobs = insertAiJobsFor(ownerId = owner.id!!, sourceLinks = savedLinks)
            }
            listOf(false, true).forEach { byOwner ->
                listOf(false, true).forEach { repeated ->
                    When("${if (byOwner) "owner가 제외" else "member가 탈퇴"}하고 반복=$repeated 이면") {
                        val delete =
                            suspend {
                                service.deleteMember(
                                    loginId = if (byOwner) owner.id!! else member.id!!,
                                    folderId = folder.id!!,
                                    memberId = member.id!!,
                                )
                            }
                        Then("대상 member만 없어지고 나머지 데이터는 유지된다") {
                            shouldNotThrow<BusinessException> { delete() }
                            if (repeated) shouldNotThrow<BusinessException> { delete() }
                            members.findByFolderIdAndUserId(folderId = folder.id!!, userId = member.id!!) shouldBe null
                            members.findByFolderIdAndUserId(folderId = folder.id!!, userId = owner.id!!)?.role shouldBe MemberRole.OWNER
                            members.findByFolderIdAndUserId(folderId = folder.id!!, userId = other.id!!)?.role shouldBe MemberRole.MEMBER
                            folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                            savedLinks.forEach {
                                val actual = links.findById(it.id!!).shouldNotBeNull()
                                actual.ownerId shouldBe it.ownerId
                                actual.folderId shouldBe it.folderId
                                actual.title shouldBe it.title
                                actual.updatedAt shouldBe it.updatedAt
                            }
                            savedTodos.forEach { todos.findById(it.id!!)?.title shouldBe it.title }
                            savedJobs.forEach { jobs.findById(it.id!!)?.prompt shouldBe it.prompt }
                            savedNotifications.forEach { notifications.findById(it.id!!)?.willFireAt shouldBe it.willFireAt }
                        }
                    }
                }
            }
            When("caller가 없으면") {
                val delete =
                    suspend { service.deleteMember(loginId = RandomFixture.randomId(), folderId = folder.id!!, memberId = member.id!!) }
                Then("OWNER_NOT_FOUND이며 멤버가 남는다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) { delete() }
                    members.findByFolderIdAndUserId(folderId = folder.id!!, userId = member.id!!).shouldNotBeNull()
                }
            }
            When("caller는 있지만 source가 없으면") {
                val delete =
                    suspend { service.deleteMember(loginId = owner.id!!, folderId = RandomFixture.randomId(), memberId = member.id!!) }
                Then("FOLDER_NOT_FOUND이며 기존 폴더는 유지된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_FOUND.message) { delete() }
                    folders.findById(folder.id!!)?.ownerId shouldBe owner.id
                }
            }
        }
    })
