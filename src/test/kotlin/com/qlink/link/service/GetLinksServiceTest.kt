package com.qlink.link.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.MatchedField
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.repository.LinkRepository
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
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.toKotlinInstant

class GetLinksServiceTest :
    BaseServiceTest({
        val getLinksService = koinGet<GetLinksService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()
        val getLinkDetailService = koinGet<GetLinkDetailService>()

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

        Given("링크 검색 서비스 테스트") {
            lateinit var user: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("최신순으로 검색하면") {
                Then("folderEmoji와 할 일 개수를 포함해 반환한다") {
                    val model = modelFixture()
                    val firstLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "검색 대상 첫 링크",
                                url = "https://example.com/first",
                                tags = listOf("검색", "첫번째"),
                                workModelId = model.id,
                                status = LinkStatus.A,
                            ),
                        )
                    val secondLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "검색 대상 둘째 링크",
                                url = "https://example.com/second",
                                tags = listOf("검색", "둘째"),
                                status = LinkStatus.G,
                                thumbnailUrl = RandomFixture.randomPreviewUrl(),
                                faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                                isVideo = true,
                            ),
                        )
                    val secondLinkId = secondLink.id!!
                    val completedAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant()
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = secondLinkId,
                            ownerId = user.id!!,
                            title = "todo-1",
                            completedAt = completedAt,
                        ),
                    )
                    todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = secondLinkId, ownerId = user.id!!, title = "todo-2"))
                    todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = secondLinkId, ownerId = user.id!!, title = "todo-3"))
                    val response =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "검색 대상",
                            folderId = null,
                            order = "LATEST",
                            scrollRequest = ScrollRequest(size = 2),
                        )

                    response.isEmpty shouldBe false
                    response.contents.shouldHaveSize(2)
                    response.contents[0].id shouldBe secondLink.id
                    response.contents[0].status shouldBe secondLink.status
                    response.contents[0].thumbnailUrl shouldBe secondLink.thumbnailUrl
                    response.contents[0].faviconUrl shouldBe secondLink.faviconUrl
                    response.contents[0].isVideo shouldBe secondLink.isVideo
                    response.contents[0].folderEmoji shouldBe null
                    response.contents[0].todoCounts.open shouldBe 2
                    response.contents[0].todoCounts.completed shouldBe 1
                    response.contents[1].id shouldBe firstLink.id
                    response.contents[1].status shouldBe firstLink.status
                    response.contents[1].folderId shouldBe folder.id
                    response.contents[1].folderName shouldBe folder.name
                    response.contents[1].folderEmoji shouldBe folder.emoji
                    response.contents[1].workModel shouldBe model.model
                    response.contents[1].todoCounts.open shouldBe 0
                    response.contents[1].todoCounts.dueSoon shouldBe 0
                    response.contents[1].todoCounts.overdue shouldBe 0
                    response.contents[1].todoCounts.completed shouldBe 0
                    todoRepository.findAllByLinkIds(listOf(firstLink.id!!)) shouldBe emptyList()
                }
            }

            When("기본·임박·기한 지남·완료 할 일이 섞여 있으면") {
                val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                val now = Clock.System.now()
                listOf(
                    TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!, reminderAt = null),
                    TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!, reminderAt = now + 1.hours),
                    TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!, reminderAt = now - 1.hours),
                    TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!, reminderAt = now - 1.hours, completedAt = now),
                ).forEach { todoRepository.insert(it) }
                val response =
                    getLinksService.getLinks(
                        loginId = user.id!!,
                        query = link.title,
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("DB의 전체 할 일 분포와 집계가 일치한다") {
                    val storedTodos = todoRepository.findAllByLinkIds(listOf(link.id!!))
                    storedTodos.size shouldBe 4
                    val counts = response.contents.single { it.id == link.id }.todoCounts
                    counts.open shouldBe 3
                    counts.dueSoon shouldBe 1
                    counts.overdue shouldBe 1
                    counts.completed shouldBe 1
                }
            }

            When("order가 비어 있으면") {
                Then("기본 최신순으로 조회한다") {
                    val firstLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "default order first",
                                url = "https://example.com/default-first",
                            ),
                        )
                    val secondLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "default order second",
                                url = "https://example.com/default-second",
                            ),
                        )

                    val response =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "default order",
                            folderId = null,
                            order = "",
                            scrollRequest = ScrollRequest(size = 10),
                        )

                    response.contents.map { it.id } shouldBe listOf(secondLink.id, firstLink.id)
                }
            }

            When("폴더 필터와 커서로 다음 페이지를 조회하면") {
                Then("다음 페이지를 이어서 조회한다") {
                    val first =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "folder search a",
                                url = "https://example.com/a",
                            ),
                        )
                    val second =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "folder search b",
                                url = "https://example.com/b",
                                thumbnailUrl = RandomFixture.randomPreviewUrl(),
                                faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                                isVideo = true,
                            ),
                        )
                    val third =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "folder search c",
                                url = "https://example.com/c",
                                thumbnailUrl = RandomFixture.randomPreviewUrl(),
                                faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                                isVideo = true,
                            ),
                        )
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = first.id!!,
                            ownerId = user.id!!,
                            completedAt = Clock.System.now(),
                        ),
                    )
                    repeat(2) {
                        todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = second.id!!, ownerId = user.id!!, reminderAt = null))
                    }
                    val firstPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "folder search",
                            folderId = folder.id,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 2),
                        )
                    val secondPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "folder search",
                            folderId = folder.id,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 2),
                        )

                    firstPage.hasNext shouldBe true
                    firstPage.contents.map { it.id } shouldBe listOf(third.id, second.id)
                    secondPage.hasNext shouldBe false
                    secondPage.contents.map { it.id } shouldBe listOf(first.id)
                    val contentsById = (firstPage.contents + secondPage.contents).associateBy { it.id }
                    listOf(first, second, third).forEach { link ->
                        val storedLink = linkRepository.findById(link.id!!)!!
                        val storedTodos = todoRepository.findAllByLinkIds(listOf(link.id!!))
                        val content = contentsById.getValue(link.id!!)
                        content.thumbnailUrl shouldBe storedLink.thumbnailUrl
                        content.faviconUrl shouldBe storedLink.faviconUrl
                        content.isVideo shouldBe storedLink.isVideo
                        content.todoCounts.open shouldBe storedTodos.count { !it.isCompleted }
                        content.todoCounts.dueSoon shouldBe 0
                        content.todoCounts.overdue shouldBe 0
                        content.todoCounts.completed shouldBe storedTodos.count { it.isCompleted }
                    }
                }
            }

            When("오래된순 커서로 다음 페이지를 조회하면") {
                Then("이전 페이지 마지막 링크 다음부터 이어서 조회한다") {
                    val first =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "earliest search a",
                                url = "https://example.com/earliest-a",
                            ),
                        )
                    val second =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "earliest search b",
                                url = "https://example.com/earliest-b",
                            ),
                        )
                    val third =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "earliest search c",
                                url = "https://example.com/earliest-c",
                            ),
                        )

                    val firstPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "earliest search",
                            folderId = null,
                            order = "earliest",
                            scrollRequest = ScrollRequest(size = 2),
                        )
                    val secondPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "earliest search",
                            folderId = null,
                            order = "earliest",
                            scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 2),
                        )

                    firstPage.contents.map { it.id } shouldBe listOf(first.id, second.id)
                    secondPage.contents.map { it.id } shouldBe listOf(third.id)
                }
            }

            When("사전순 커서로 다음 페이지를 조회하면") {
                Then("title 기준 다음 링크부터 이어서 조회한다") {
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "alpha search a",
                            url = "https://example.com/laxico-a",
                        ),
                    )
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "alpha search b",
                            url = "https://example.com/laxico-b",
                        ),
                    )
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "alpha search c",
                            url = "https://example.com/laxico-c",
                        ),
                    )

                    val firstPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "alpha search",
                            folderId = null,
                            order = "laxico",
                            scrollRequest = ScrollRequest(size = 2),
                        )
                    val secondPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "alpha search",
                            folderId = null,
                            order = "laxico",
                            scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 2),
                        )

                    firstPage.contents.map { it.title } shouldBe listOf("alpha search a", "alpha search b")
                    secondPage.contents.map { it.title } shouldBe listOf("alpha search c")
                }
            }

            When("유사도순으로 검색하면") {
                Then("유사도가 높은 순서로 반환한다") {
                    val exact =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "카카오",
                                url = "https://example.com/kakao",
                                tags = listOf("메신저"),
                            ),
                        )
                    val partial =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "카카오톡 모아보기",
                                url = "https://example.com/kakaotalk",
                                tags = listOf("카카오"),
                            ),
                        )
                    val response =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "카카오",
                            folderId = null,
                            order = "similar",
                            scrollRequest = ScrollRequest(size = 10),
                        )

                    response.contents.map { it.id } shouldBe listOf(exact.id, partial.id)
                }
            }

            When("유사도순 커서로 다음 페이지를 조회하면") {
                Then("이전 페이지 마지막 결과 다음부터 이어서 조회한다") {
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "카카오",
                            url = "https://example.com/kakao-exact",
                            tags = listOf("메신저"),
                        ),
                    )
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "카카오톡 모아보기",
                            url = "https://example.com/kakao-partial",
                            tags = listOf("카카오"),
                        ),
                    )
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "오늘의 카카오 뉴스",
                            url = "https://example.com/kakao-news",
                            tags = listOf("뉴스"),
                        ),
                    )

                    val firstPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "카카오",
                            folderId = null,
                            order = "similar",
                            scrollRequest = ScrollRequest(size = 2),
                        )
                    val secondPage =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "카카오",
                            folderId = null,
                            order = "similar",
                            scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 2),
                        )

                    firstPage.hasNext shouldBe true
                    firstPage.contents.shouldHaveSize(2)
                    secondPage.contents.shouldHaveSize(1)
                }
            }

            When("isFavorite=true 필터로 검색하면") {
                val favorited =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "favorite filter a",
                            url = "https://example.com/favorite-a",
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                linkRepository.insert(
                    LinkFixture.createRandomLinkOf(
                        ownerId = user.id!!,
                        title = "favorite filter b",
                        url = "https://example.com/favorite-b",
                    ),
                )
                val response =
                    getLinksService.getLinks(
                        loginId = user.id!!,
                        query = "favorite filter",
                        folderId = null,
                        isFavorite = true,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 10),
                    )

                Then("바로가기 링크만 isFavorite=true로 반환한다") {
                    response.contents.map { it.id } shouldBe listOf(favorited.id)
                    response.contents.all { it.isFavorite } shouldBe true
                }
            }

            When("isFavorite=false 필터로 검색하면") {
                linkRepository.insert(
                    LinkFixture.createRandomLinkOf(
                        ownerId = user.id!!,
                        title = "non favorite filter a",
                        url = "https://example.com/non-favorite-a",
                        favoriteAt = RandomFixture.randomPastInstant(),
                    ),
                )
                val notFavorited =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "non favorite filter b",
                            url = "https://example.com/non-favorite-b",
                        ),
                    )
                val response =
                    getLinksService.getLinks(
                        loginId = user.id!!,
                        query = "non favorite filter",
                        folderId = null,
                        isFavorite = false,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 10),
                    )

                Then("바로가기가 아닌 링크만 반환한다") {
                    response.contents.map { it.id } shouldBe listOf(notFavorited.id)
                    response.contents.all { it.isFavorite } shouldBe false
                }
            }

            When("isFavorite 필터가 null이면") {
                val favorited =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "no favorite filter a",
                            url = "https://example.com/no-filter-a",
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                val notFavorited =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            title = "no favorite filter b",
                            url = "https://example.com/no-filter-b",
                        ),
                    )
                val response =
                    getLinksService.getLinks(
                        loginId = user.id!!,
                        query = "no favorite filter",
                        folderId = null,
                        isFavorite = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 10),
                    )

                Then("바로가기 여부와 무관하게 모두 반환한다") {
                    response.contents.map { it.id }.toSet() shouldBe setOf(favorited.id, notFavorited.id)
                }
            }

            When("size가 0 이하면") {
                Then("기본 스크롤 크기로 조회한다") {
                    repeat(16) { index ->
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "default size search $index",
                                url = "https://example.com/default-size-$index",
                            ),
                        )
                    }

                    val response =
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "default size search",
                            folderId = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 0),
                        )

                    response.contents.shouldHaveSize(15)
                    response.hasNext shouldBe true
                    response.nextCursor.shouldNotBeNull()
                }
            }

            When("cursor가 잘못되면") {
                val getLinks =
                    suspend {
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "anything",
                            folderId = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = "not-base64"),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        getLinks()
                    }
                }
            }

            When("order가 지원하지 않는 값이면") {
                val getLinks =
                    suspend {
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "anything",
                            folderId = null,
                            order = "unknown",
                            scrollRequest = ScrollRequest(),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        getLinks()
                    }
                }
            }

            When("cursor의 order와 요청 order가 다르면") {
                val getLinks =
                    suspend {
                        getLinksService.getLinks(
                            loginId = user.id!!,
                            query = "anything",
                            folderId = null,
                            order = "latest",
                            scrollRequest =
                                ScrollRequest(
                                    cursor =
                                        createCursor(
                                            order = LinkSearchOrder.EARLIEST,
                                            value = LinkSearchCursorValue(id = 1L),
                                        ),
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        getLinks()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val getLinks =
                    suspend {
                        getLinksService.getLinks(
                            loginId = RandomFixture.randomId(),
                            query = "anything",
                            folderId = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        getLinks()
                    }
                }
            }
        }

        Given("공유 폴더 멤버가 보는 링크") {
            lateinit var member: User
            lateinit var sharedLink: Link

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
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
                todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = sharedLink.id!!, ownerId = owner.id!!, reminderAt = null))
            }

            When("목록을 조회하면") {
                val linkId = sharedLink.id!!
                val response =
                    getLinksService.getLinks(
                        loginId = member.id!!,
                        query = null,
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("저장된 이미지 값과 할 일 개수를 볼 수 있다") {
                    val stored = linkRepository.findById(linkId)!!
                    val content = response.contents.single { it.id == linkId }
                    content.thumbnailUrl shouldBe stored.thumbnailUrl
                    content.faviconUrl shouldBe stored.faviconUrl
                    content.isVideo shouldBe stored.isVideo
                    content.todoCounts.open shouldBe 1
                }
            }
        }

        Given("생성 시각과 id 순서가 다른 내 링크들(가져온 옛 링크 포함)") {
            lateinit var user: User
            lateinit var links: List<Link>

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val base = RandomFixture.randomPastInstant()
                links =
                    listOf(
                        LinkFixture.createRandomLinkOf(ownerId = user.id!!, createdAt = base + 3.hours),
                        LinkFixture.createImportedLinkOf(ownerId = user.id!!, createdAt = base),
                        LinkFixture.createRandomLinkOf(ownerId = user.id!!, createdAt = base + 1.hours),
                        LinkFixture.createRandomLinkOf(ownerId = user.id!!, createdAt = base + 1.hours),
                    ).map { linkRepository.insert(it) }
            }

            suspend fun storedLatestOrder(): List<Long> =
                linkRepository
                    .findAllByIds(links.map { it.id!! })
                    .sortedWith(compareByDescending<Link> { it.createdAt }.thenByDescending { it.id })
                    .map { it.id!! }

            suspend fun getLinks(
                order: String,
                cursor: String? = null,
                size: Int = 10,
            ) = getLinksService.getLinks(
                loginId = user.id!!,
                query = null,
                folderId = null,
                order = order,
                scrollRequest = ScrollRequest(cursor = cursor, size = size),
            )

            When("latest로 조회하면") {
                val get = suspend { getLinks(order = "latest") }

                Then("createdAt 내림차순, 같으면 id 내림차순이다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder()
                    response.contents.map { it.id } shouldBe listOf(links[0].id, links[3].id, links[2].id, links[1].id)
                }
            }

            When("earliest로 조회하면") {
                val get = suspend { getLinks(order = "earliest") }

                Then("createdAt 오름차순, 같으면 id 오름차순이다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder().reversed()
                }
            }

            When("같은 createdAt 링크가 페이지 경계에 걸쳐 새 커서로 이어 읽으면") {
                val get =
                    suspend {
                        val firstPage = getLinks(order = "latest", size = 2)
                        firstPage to getLinks(order = "latest", cursor = firstPage.nextCursor, size = 2)
                    }

                Then("빠짐·중복 없이 이어진다") {
                    val (firstPage, secondPage) = get()

                    firstPage.hasNext shouldBe true
                    secondPage.hasNext shouldBe false
                    (firstPage.contents + secondPage.contents).map { it.id } shouldBe storedLatestOrder()
                }
            }

            When("createdAt이 없는 옛 커서(id만)로 이어 읽으면") {
                val get =
                    suspend {
                        getLinks(
                            order = "latest",
                            cursor = createCursor(order = LinkSearchOrder.LATEST, value = LinkSearchCursorValue(id = links[3].id)),
                        )
                    }

                Then("그 id 행의 createdAt 기준으로 이어진다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder().dropWhile { it != links[3].id }.drop(1)
                }
            }

            When("옛 커서의 id 행이 지워졌으면") {
                val get =
                    suspend {
                        val deletedId = links[3].id!!
                        linkRepository.deleteById(deletedId)
                        getLinks(
                            order = "latest",
                            cursor = createCursor(order = LinkSearchOrder.LATEST, value = LinkSearchCursorValue(id = deletedId)),
                        )
                    }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }
        }

        Given("제목에만, 본문에만, 메모에만 검색어가 있는 링크들") {
            lateinit var fixture: ContentSearchFixture

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                val base = RandomFixture.randomPastInstant()

                suspend fun insertLink(
                    hours: Int,
                    title: String = RandomFixture.randomSentenceWithMax(30),
                    memo: String? = null,
                ): Link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            title = title,
                            memo = memo,
                            summary = null,
                            tags = emptyList(),
                            url = "https://example.com/${RandomFixture.randomId()}",
                            createdAt = base + hours.hours,
                        ),
                    )

                val titleOnly = insertLink(hours = 1, title = SEARCH_KEYWORD)
                val memoOnly = insertLink(hours = 3, memo = "메모 $SEARCH_KEYWORD 메모")
                val titleAndBody = insertLink(hours = 0, title = "$SEARCH_KEYWORD 둘 다")
                val bodyOnly = insertLink(hours = 4)
                val shortBodyOnly = insertLink(hours = 2)
                val longBody = bodyTextWith(keyword = SEARCH_KEYWORD, before = 40, after = 300)
                val shortBody = bodyTextWith(keyword = SEARCH_KEYWORD, before = 20, after = 26)
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createCollectedContentOf(linkId = bodyOnly.id!!, text = longBody),
                        LinkContentFixture.createCollectedContentOf(linkId = shortBodyOnly.id!!, text = shortBody),
                        LinkContentFixture.createCollectedContentOf(
                            linkId = titleAndBody.id!!,
                            text = bodyTextWith(keyword = SEARCH_KEYWORD, before = 30, after = 200),
                        ),
                    ),
                )
                fixture =
                    ContentSearchFixture(
                        ownerId = owner.id!!,
                        titleOnly = titleOnly.id!!,
                        memoOnly = memoOnly.id!!,
                        titleAndBody = titleAndBody.id!!,
                        bodyOnly = bodyOnly.id!!,
                        shortBodyOnly = shortBodyOnly.id!!,
                        shortBodyLength = shortBody.length,
                    )
            }

            suspend fun search(
                ownerId: Long,
                query: String?,
                order: String = "latest",
                cursor: String? = null,
                size: Int = 20,
            ) = getLinksService.getLinks(
                loginId = ownerId,
                query = query,
                folderId = null,
                order = order,
                scrollRequest = ScrollRequest(cursor = cursor, size = size),
            )

            /** 검색어로 size 2씩 끝까지 커서를 따라 읽은 페이지별 id. */
            suspend fun allPages(
                ownerId: Long,
                order: String,
            ): List<List<Long>> {
                val pages = mutableListOf<List<Long>>()
                var cursor: String? = null
                do {
                    val page = search(ownerId = ownerId, query = SEARCH_KEYWORD, order = order, cursor = cursor, size = 2)
                    pages += page.contents.map { it.id }
                    cursor = page.nextCursor
                } while (cursor != null)
                return pages
            }

            When("검색하면") {
                val links = fixture
                val response = search(ownerId = links.ownerId, query = SEARCH_KEYWORD)

                Then("메타데이터 일치(제목·메모) 링크가 먼저, 본문만 일치 링크가 뒤에 오고 각 묶음 안은 요청한 정렬이다") {
                    response.contents.map { it.id } shouldBe
                        listOf(links.memoOnly, links.titleOnly, links.titleAndBody, links.bodyOnly, links.shortBodyOnly)
                    response.contents.single { it.id == links.memoOnly }.matchedFields shouldBe listOf(MatchedField.MEMO)
                    response.contents.single { it.id == links.titleOnly }.matchedFields shouldBe listOf(MatchedField.TITLE)
                }
            }

            When("본문만 일치한 항목이면") {
                val links = fixture
                val item = search(ownerId = links.ownerId, query = SEARCH_KEYWORD).contents.single { it.id == links.bodyOnly }

                Then("matchedFields가 [CONTENT]이고 contentSnippet.text가 100 유닛 이하이며 강조 범위가 유효하다") {
                    item.matchedFields shouldBe listOf(MatchedField.CONTENT)
                    val snippet = item.contentSnippet.shouldNotBeNull()
                    snippet.text.length shouldBeLessThanOrEqual 100
                    snippet.highlights.shouldNotBeEmpty()
                    snippet.highlights.forEach { range ->
                        (0 <= range.start && range.start < range.end && range.end <= snippet.text.length) shouldBe true
                        snippet.text.substring(range.start, range.end) shouldBe SEARCH_KEYWORD
                    }
                    snippet.highlights.zipWithNext().forEach { (left, right) -> (left.end <= right.start) shouldBe true }
                }
            }

            When("본문이 100자 미만인 링크가 본문으로 일치하면") {
                val links = fixture
                val item = search(ownerId = links.ownerId, query = SEARCH_KEYWORD).contents.single { it.id == links.shortBodyOnly }

                Then("contentSnippet의 보이는 글자가 본문 길이의 30% 이하(최소 일치어)다") {
                    val visible =
                        item.contentSnippet
                            .shouldNotBeNull()
                            .text
                            .removePrefix("…")
                            .removeSuffix("…")
                    visible.length shouldBeLessThanOrEqual maxOf(SEARCH_KEYWORD.length, links.shortBodyLength * 3 / 10)
                    visible shouldContain SEARCH_KEYWORD
                }
            }

            When("제목과 본문이 모두 일치하면") {
                val links = fixture
                val response = search(ownerId = links.ownerId, query = SEARCH_KEYWORD)
                val item = response.contents.single { it.id == links.titleAndBody }

                Then("matchedFields에 둘 다 있고 스니펫도 있으며 메타 묶음에 속한다") {
                    item.matchedFields shouldBe listOf(MatchedField.TITLE, MatchedField.CONTENT)
                    item.contentSnippet.shouldNotBeNull()
                    response.contents.indexOf(item) shouldBeLessThan response.contents.indexOfFirst { it.id == links.bodyOnly }
                }
            }

            When("검색어가 1자이면") {
                val links = fixture
                val response = search(ownerId = links.ownerId, query = SEARCH_KEYWORD.take(1))

                Then("본문만 일치한 링크가 결과에 없다") {
                    response.contents.map { it.id } shouldBe listOf(links.memoOnly, links.titleOnly, links.titleAndBody)
                    response.contents.forEach { it.contentSnippet shouldBe null }
                }
            }

            When("검색어가 없으면") {
                val links = fixture
                val response = search(ownerId = links.ownerId, query = null)

                Then("matchedFields가 비고 contentSnippet이 null이며 순서가 기존과 같다") {
                    response.contents.map { it.id } shouldBe
                        listOf(links.bodyOnly, links.memoOnly, links.shortBodyOnly, links.titleOnly, links.titleAndBody)
                    response.contents.forEach {
                        it.matchedFields.shouldBeEmpty()
                        it.contentSnippet shouldBe null
                    }
                }
            }

            When("NFD로 입력된 한글 검색어이면") {
                val links = fixture
                val response = search(ownerId = links.ownerId, query = Normalizer.normalize(SEARCH_KEYWORD, Normalizer.Form.NFD))

                Then("NFC 본문과 일치한다") {
                    response.contents.single { it.id == links.bodyOnly }.matchedFields shouldBe listOf(MatchedField.CONTENT)
                }
            }

            When("검색어가 있는 결과의 두 번째 페이지를 커서로 요청하면") {
                val links = fixture
                val pages = allPages(ownerId = links.ownerId, order = "latest")

                Then("메타 묶음 경계와 동률을 건너뛰지 않고 이어진다") {
                    pages shouldBe
                        listOf(
                            listOf(links.memoOnly, links.titleOnly),
                            listOf(links.titleAndBody, links.bodyOnly),
                            listOf(links.shortBodyOnly),
                        )
                }
            }

            When("검색어가 있는데 커서에 isMetadataMatched가 없으면") {
                val links = fixture
                val getLinks =
                    suspend {
                        search(
                            ownerId = links.ownerId,
                            query = SEARCH_KEYWORD,
                            cursor =
                                createCursor(
                                    order = LinkSearchOrder.LATEST,
                                    value = LinkSearchCursorValue(id = links.titleOnly, createdAt = RandomFixture.randomPastInstant()),
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        getLinks()
                    }
                }
            }

            When("similar 정렬로 검색하면") {
                val links = fixture
                val pages = allPages(ownerId = links.ownerId, order = "similar")

                Then("메타 일치 묶음이 본문만 일치 묶음보다 앞이고 묶음 안은 점수순이다") {
                    pages.flatten() shouldBe
                        listOf(links.titleOnly, links.titleAndBody, links.memoOnly, links.shortBodyOnly, links.bodyOnly)
                }
            }
        }

        Given("필드 경계를 가로질러서만 이어지는 검색어") {
            var ownerId = 0L
            lateinit var crossing: Link

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                ownerId = owner.id!!
                crossing =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            title = "제목 ${SEARCH_KEYWORD}끝",
                            url = "https://example.com/${RandomFixture.randomId()}",
                            summary = null,
                            memo = null,
                            tags = emptyList(),
                        ),
                    )
            }

            When("제목 끝과 URL 앞을 이은 검색어로 검색하면") {
                val linkId = crossing.id!!
                val response =
                    getLinksService.getLinks(
                        loginId = ownerId,
                        query = "${SEARCH_KEYWORD}끝 https",
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("search_text에만 일치하는 링크는 결과에 없다") {
                    response.contents.map { it.id } shouldNotContain linkId
                }
            }
        }

        Given("_·%·\\가 든 제목과 그 글자 자리에 다른 글자가 든 제목") {
            var ownerId = 0L
            lateinit var titles: Map<String, Long>

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                ownerId = owner.id!!
                titles =
                    listOf("뷁a_b", "뷁axb", "뷁50%", "뷁50x", "뷁c\\d", "뷁cxd").associateWith { title ->
                        linkRepository
                            .insert(
                                LinkFixture.createRandomLinkOf(
                                    ownerId = owner.id!!,
                                    title = title,
                                    url = "https://example.com/${RandomFixture.randomId()}",
                                    summary = null,
                                    memo = null,
                                    tags = emptyList(),
                                ),
                            ).id!!
                    }
            }

            When("검색어에 _·%·\\가 있으면") {
                val ids = titles
                val results =
                    listOf("뷁a_b", "뷁50%", "뷁c\\d").associateWith { query ->
                        getLinksService
                            .getLinks(
                                loginId = ownerId,
                                query = query,
                                folderId = null,
                                order = "latest",
                                scrollRequest = ScrollRequest(),
                            ).contents
                            .map { it.id }
                    }

                Then("그 글자가 실제로 든 링크만 나온다") {
                    results shouldBe
                        mapOf(
                            "뷁a_b" to listOf(ids.getValue("뷁a_b")),
                            "뷁50%" to listOf(ids.getValue("뷁50%")),
                            "뷁c\\d" to listOf(ids.getValue("뷁c\\d")),
                        )
                }
            }
        }

        Given("내가 멤버가 아닌 공유 폴더의 링크 본문이 검색어를 담고 있을 때") {
            var searcherId = 0L
            lateinit var foreignLink: Link

            beforeTest {
                val searcher = userRepository.insert(UserFixture.createRandomValidUser())
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                searcherId = searcher.id!!
                val sharedFolder =
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createOwnerMember(folderId = sharedFolder.id!!, userId = owner.id!!),
                )
                foreignLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = sharedFolder.id, summary = null, memo = null),
                    )
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createCollectedContentOf(
                            linkId = foreignLink.id!!,
                            text = bodyTextWith(keyword = SEARCH_KEYWORD, before = 10, after = 200),
                        ),
                    ),
                )
            }

            When("비멤버가 검색하면") {
                val linkId = foreignLink.id!!
                val response =
                    getLinksService.getLinks(
                        loginId = searcherId,
                        query = SEARCH_KEYWORD,
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("결과에 없다") {
                    response.contents.map { it.id } shouldNotContain linkId
                }
            }
        }

        Given("발췌가 있는 링크와 없는 링크") {
            var ownerId = 0L
            lateinit var linkIds: List<Long>

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                ownerId = owner.id!!
                val collected = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))
                val pending = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))
                val withoutRow = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createCollectedContentOf(linkId = collected.id!!),
                        LinkContentFixture.createPendingContentOf(linkId = pending.id!!),
                    ),
                )
                linkIds = listOf(collected.id!!, pending.id!!, withoutRow.id!!)
            }

            When("검색 없이 목록을 받으면") {
                val loginId = ownerId
                val (collectedId, pendingId, withoutRowId) = linkIds
                val response =
                    getLinksService.getLinks(
                        loginId = loginId,
                        query = null,
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("COLLECTED 링크만 contentExcerpt가 상세 발췌와 같고 나머지는 null이다") {
                    val storedExcerpt =
                        linkContentRepository
                            .findByLinkId(collectedId)
                            .shouldNotBeNull()
                            .excerpt
                            .shouldNotBeNull()
                    val byId = response.contents.associateBy { it.id }
                    byId.getValue(collectedId).contentExcerpt shouldBe storedExcerpt
                    byId.getValue(collectedId).contentExcerpt shouldBe
                        getLinkDetailService.getLinkDetail(loginId, collectedId).content?.excerpt
                    byId.getValue(pendingId).contentExcerpt shouldBe null
                    byId.getValue(withoutRowId).contentExcerpt shouldBe null
                }
            }
        }
    })

private fun createCursor(
    order: LinkSearchOrder,
    value: LinkSearchCursorValue,
): String = Base64CursorCodec.encode(LinkSearchCursor(order = order, value = value))

/** 다른 필드·채움 글자에 우연히 들어 있지 않을 검색어. */
private const val SEARCH_KEYWORD = "뷁햏쀍뷁"

/** 영문 소문자·숫자 채움 글자 사이에 [keyword]를 둔 본문. */
private fun bodyTextWith(
    keyword: String,
    before: Int,
    after: Int,
): String = LinkContentFixture.randomBodyText(before) + keyword + LinkContentFixture.randomBodyText(after)

private class ContentSearchFixture(
    val ownerId: Long,
    val titleOnly: Long,
    val memoOnly: Long,
    val titleAndBody: Long,
    val bodyOnly: Long,
    val shortBodyOnly: Long,
    val shortBodyLength: Int,
)
