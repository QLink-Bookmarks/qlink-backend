package com.qlink.link.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.LinkContentResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import java.time.LocalTime
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

class GetLinkDetailServiceTest :
    BaseServiceTest({
        val getLinkDetailService = koinGet<GetLinkDetailService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()

        suspend fun modelFixture(): AvailableModel {
            val provider =
                AiFixture
                    .createRandomValidAiProvider(type = AiProviderType.CLAUDE)
                    .let { aiProvider -> aiProviderRepository.findByType(aiProvider.type) ?: aiProviderRepository.insert(aiProvider) }

            return availableModelRepository
                .findAllByProviderId(provider.id!!)
                .firstOrNull()
                ?: availableModelRepository.insert(AiFixture.createRandomAvailableModelOf(providerId = provider.id))
        }

        Given("링크 상세 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("폴더와 할 일이 있는 본인 링크 상세 조회를") {
                val model = modelFixture()
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            folderId = folder.id,
                            workModelId = model.id,
                            status = LinkStatus.A,
                            thumbnailUrl = RandomFixture.randomPreviewUrl(),
                            faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                            isVideo = true,
                        ),
                    )
                val otherLink =
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                val todos =
                    listOf(
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = link.id!!,
                                ownerId = user.id!!,
                                repeatUntil =
                                    RandomFixture
                                        .futureDateTime(30, java.util.concurrent.TimeUnit.DAYS)
                                        .toInstant()
                                        .toKotlinInstant(),
                                repeatDays = listOf(RepeatDay.FRI),
                                repeatTime = LocalTime.of(7, 30),
                                repeatTimezone = java.time.ZoneId.of("Asia/Seoul"),
                            ),
                        ),
                        todoRepository.insert(
                            TodoFixture.createRandomTodoOf(
                                linkId = link.id,
                                ownerId = user.id!!,
                                completedAt =
                                    RandomFixture
                                        .randomDateTime()
                                        .toInstant()
                                        .toKotlinInstant(),
                            ),
                        ),
                    )
                todoRepository.insert(
                    TodoFixture.createRandomTodoOf(
                        linkId = otherLink.id!!,
                        ownerId = user.id!!,
                    ),
                )
                val expectedFolderId = folder.id
                val expectedFolderName = folder.name
                val expectedFolderEmoji = folder.emoji
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id)

                Then("성공한다") {
                    actual.id shouldBe link.id
                    actual.url shouldBe link.url
                    actual.title shouldBe link.title
                    actual.thumbnailUrl shouldBe link.thumbnailUrl
                    actual.faviconUrl shouldBe link.faviconUrl
                    actual.isVideo shouldBe link.isVideo
                    actual.summary shouldBe link.summary
                    actual.tags shouldBe link.tags
                    actual.memo shouldBe link.memo
                    actual.sourceType shouldBe link.sourceType
                    actual.status shouldBe link.status
                    actual.createdAt shouldBe link.createdAt
                    actual.folderId shouldBe expectedFolderId
                    actual.folderName shouldBe expectedFolderName
                    actual.folderEmoji shouldBe expectedFolderEmoji
                    actual.workModel shouldBe model.model
                    actual.todos shouldContainExactly todos.map { it.toExpectedTodo() }
                    actual.isFavorite shouldBe false
                }
            }

            When("바로가기로 지정된 본인 링크 상세 조회를") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id!!)

                Then("isFavorite가 true로 조회된다") {
                    actual.id shouldBe link.id
                    actual.isFavorite shouldBe true
                }
            }

            When("폴더가 없는 본인 링크 상세 조회를") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id!!)

                Then("폴더 정보 없이 성공한다") {
                    actual.id shouldBe link.id
                    actual.folderId shouldBe null
                    actual.folderName shouldBe null
                    actual.folderEmoji shouldBe null
                    actual.workModel shouldBe null
                    actual.todos shouldBe emptyList()
                }
            }

            When("없는 링크 상세 조회를") {
                val invalidLinkId = RandomFixture.randomId()
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, invalidLinkId)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        getDetail()
                    }
                }
            }

            When("다른 사용자의 링크 상세 조회를") {
                val link: Link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = otherUser.id!!,
                        ),
                    )
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, link.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        getDetail()
                    }
                }
            }
        }

        Given("공유 폴더의 이미지가 있는 링크") {
            lateinit var member: User
            lateinit var outsider: User
            lateinit var sharedLink: Link

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                outsider = userRepository.insert(UserFixture.createRandomValidUser())
                val sharedFolder =
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createOwnerMember(folderId = sharedFolder.id!!, userId = owner.id!!),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = sharedFolder.id!!, userId = member.id!!),
                )
                sharedLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            folderId = sharedFolder.id,
                            thumbnailUrl = RandomFixture.randomPreviewUrl(),
                            faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                            isVideo = true,
                        ),
                    )
            }

            When("멤버가 상세를 조회하면") {
                val linkId = sharedLink.id!!
                val response = getLinkDetailService.getLinkDetail(member.id!!, linkId)

                Then("저장된 이미지 값이 보인다") {
                    val stored = linkRepository.findById(linkId)!!
                    response.thumbnailUrl shouldBe stored.thumbnailUrl
                    response.faviconUrl shouldBe stored.faviconUrl
                    response.isVideo shouldBe stored.isVideo
                }
            }

            When("비회원이 상세를 조회하면") {
                val getDetail = suspend { getLinkDetailService.getLinkDetail(outsider.id!!, sharedLink.id!!) }

                Then("공유 폴더 접근 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED.message) { getDetail() }
                }
            }
        }

        Given("본문 행 상태가 각기 다른 링크들") {
            lateinit var fixture: ContentDetailFixture

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                val member = userRepository.insert(UserFixture.createRandomValidUser())
                val sharedFolder =
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createOwnerMember(folderId = sharedFolder.id!!, userId = owner.id!!),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = sharedFolder.id!!, userId = member.id!!),
                )

                suspend fun insertLink(): Long =
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = sharedFolder.id)).id!!

                val now = RandomFixture.randomPastInstant()
                val withoutRow = insertLink()
                val pending = insertLink()
                val collected = insertLink()
                val tinyCollected = insertLink()
                val failed = insertLink()
                val blocked = insertLink()
                val collectedText = LinkContentFixture.randomBodyText(RandomFixture.randomInt(200, 2_000))
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createPendingContentOf(linkId = pending),
                        LinkContentFixture.createCollectedContentOf(linkId = collected, text = collectedText),
                        LinkContentFixture.createCollectedContentOf(linkId = tinyCollected, text = LinkContentFixture.randomBodyText(3)),
                        LinkContentFixture
                            .createClaimedContentOf(linkId = failed)
                            .fail(code = ContentFailureCode.NOT_HTML, retryAfterSeconds = null, now = now),
                        LinkContentFixture
                            .createClaimedContentOf(linkId = blocked)
                            .fail(code = ContentFailureCode.LOGIN_REQUIRED, retryAfterSeconds = null, now = now),
                    ),
                )
                fixture =
                    ContentDetailFixture(
                        ownerId = owner.id!!,
                        memberId = member.id!!,
                        withoutRow = withoutRow,
                        pending = pending,
                        collected = collected,
                        collectedText = collectedText,
                        tinyCollected = tinyCollected,
                        failed = failed,
                        blocked = blocked,
                    )
            }

            When("행이 없으면") {
                val links = fixture
                val response = getLinkDetailService.getLinkDetail(links.ownerId, links.withoutRow)

                Then("content가 null이다") {
                    response.content shouldBe null
                }
            }

            When("PENDING이면") {
                val links = fixture
                val response = getLinkDetailService.getLinkDetail(links.ownerId, links.pending)

                Then("content.excerpt가 null이고 isPending이 참이다") {
                    response.content shouldBe LinkContentResponse(excerpt = null, isPending = true)
                }
            }

            When("COLLECTED이면") {
                val links = fixture
                val response = getLinkDetailService.getLinkDetail(links.ownerId, links.collected)

                Then("content.excerpt가 발췌이고 isPending이 거짓이며 본문 전체는 응답에 없다") {
                    val stored = linkContentRepository.findByLinkId(links.collected).shouldNotBeNull()
                    response.content shouldBe LinkContentResponse(excerpt = stored.excerpt.shouldNotBeNull(), isPending = false)
                    Json.encodeToString(response) shouldNotContain links.collectedText
                }
            }

            When("COLLECTED인데 발췌가 없는(3자 이하) 본문이면") {
                val links = fixture
                val response = getLinkDetailService.getLinkDetail(links.ownerId, links.tinyCollected)

                Then("content.excerpt가 null이고 isPending이 거짓이다") {
                    linkContentRepository.findByLinkId(links.tinyCollected).shouldNotBeNull().status shouldBe ContentStatus.COLLECTED
                    response.content shouldBe LinkContentResponse(excerpt = null, isPending = false)
                }
            }

            When("FAILED·BLOCKED이면") {
                val links = fixture
                val failed = getLinkDetailService.getLinkDetail(links.ownerId, links.failed)
                val blocked = getLinkDetailService.getLinkDetail(links.ownerId, links.blocked)

                Then("content.excerpt가 null이고 isPending이 거짓이다") {
                    failed.content shouldBe LinkContentResponse(excerpt = null, isPending = false)
                    blocked.content shouldBe LinkContentResponse(excerpt = null, isPending = false)
                }
            }

            When("공유 폴더 멤버가 보면") {
                val links = fixture
                val asOwner = getLinkDetailService.getLinkDetail(links.ownerId, links.collected)
                val asMember = getLinkDetailService.getLinkDetail(links.memberId, links.collected)

                Then("소유자와 같은 content가 나온다") {
                    asMember.content.shouldNotBeNull()
                    asMember.content shouldBe asOwner.content
                }
            }
        }
    })

private fun Todo.toExpectedTodo() =
    LinkDetailTodoQuery(
        id = id!!,
        title = title,
        completedAt = completedAt,
        reminderAt = reminderAt,
        repeatUntil = repeatUntil,
        repeatDays = repeatDays,
        repeatTime = repeatTime?.toString(),
    )

private class ContentDetailFixture(
    val ownerId: Long,
    val memberId: Long,
    val withoutRow: Long,
    val pending: Long,
    val collected: Long,
    val collectedText: String,
    val tinyCollected: Long,
    val failed: Long,
    val blocked: Long,
)
