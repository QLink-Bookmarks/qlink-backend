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
import com.qlink.common.search.SearchOrder
import com.qlink.common.search.SortDirection
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkOrderKey
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.MatchedField
import com.qlink.link.domain.Snippet
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
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
import kotlin.time.Instant
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

        suspend fun insertContentSearchFixture(
            titleOnlyHours: Int = 1,
            memoOnlyHours: Int = 3,
            titleAndBodyHours: Int = 0,
            bodyOnlyHours: Int = 4,
            shortBodyOnlyHours: Int = 2,
            withNearMiss: Boolean = false,
        ): ContentSearchFixture {
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

            val titleOnly = insertLink(hours = titleOnlyHours, title = SEARCH_KEYWORD)
            val memoOnly = insertLink(hours = memoOnlyHours, memo = "메모 $SEARCH_KEYWORD 메모")
            val titleAndBody = insertLink(hours = titleAndBodyHours, title = "$SEARCH_KEYWORD 둘 다")
            val shortBodyOnly = insertLink(hours = shortBodyOnlyHours)
            val bodyOnly = insertLink(hours = bodyOnlyHours)
            val nearMiss = if (withNearMiss) insertLink(hours = 5, title = SEARCH_KEYWORD.take(3)) else null
            val longBody = bodyTextWith(keyword = SEARCH_KEYWORD, before = 40, after = 300)
            val shortBody = bodyTextWith(keyword = SEARCH_KEYWORD, before = 20, after = 26)
            linkContentRepository.insertAll(
                listOfNotNull(
                    LinkContentFixture.createCollectedContentOf(linkId = bodyOnly.id!!, text = longBody),
                    LinkContentFixture.createCollectedContentOf(linkId = shortBodyOnly.id!!, text = shortBody),
                    LinkContentFixture.createCollectedContentOf(
                        linkId = titleAndBody.id!!,
                        text = bodyTextWith(keyword = SEARCH_KEYWORD, before = 30, after = 200),
                    ),
                    nearMiss?.let {
                        LinkContentFixture.createCollectedContentOf(
                            linkId = it.id!!,
                            text = bodyTextWith(keyword = SEARCH_KEYWORD, before = 40, after = 300),
                        )
                    },
                ),
            )
            return ContentSearchFixture(
                ownerId = owner.id!!,
                titleOnly = titleOnly.id!!,
                memoOnly = memoOnly.id!!,
                titleAndBody = titleAndBody.id!!,
                bodyOnly = bodyOnly.id!!,
                shortBodyOnly = shortBodyOnly.id!!,
                nearMiss = nearMiss?.id,
                bodyOnlyText = longBody,
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

        suspend fun allPages(
            ownerId: Long,
            order: String,
            size: Int = 2,
        ): List<List<Long>> {
            val pages = mutableListOf<List<Long>>()
            var cursor: String? = null
            do {
                val page = search(ownerId = ownerId, query = SEARCH_KEYWORD, order = order, cursor = cursor, size = size)
                pages += page.contents.map { it.id }
                cursor = page.nextCursor
            } while (cursor != null)
            return pages
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
                            cursor = createCursor(order = SearchOrder.LATEST, value = LinkSearchCursorValue(id = links[3].id)),
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
                            cursor = createCursor(order = SearchOrder.LATEST, value = LinkSearchCursorValue(id = deletedId)),
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
                fixture = insertContentSearchFixture()
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

                Then("matchedFields가 [CONTENT]이고 contentSnippet.text가 일치 앞 15자부터 자른 100 유닛 이하 창이며 강조 범위가 유효하다") {
                    item.matchedFields shouldBe listOf(MatchedField.CONTENT)
                    val snippet = item.contentSnippet.shouldNotBeNull()
                    snippet.text.length shouldBeLessThanOrEqual 100
                    snippet.highlights.shouldNotBeEmpty()
                    snippet.highlights.forEach { range ->
                        (0 <= range.start && range.start < range.end && range.end <= snippet.text.length) shouldBe true
                        snippet.text.substring(range.start, range.end) shouldBe SEARCH_KEYWORD
                    }
                    snippet.highlights.zipWithNext().forEach { (left, right) -> (left.end <= right.start) shouldBe true }
                    snippet.text shouldBe
                        "…" +
                        links.bodyOnlyText.substring(
                            40 - Snippet.CONTEXT_BEFORE,
                            40 - Snippet.CONTEXT_BEFORE + Snippet.SOURCE_MAX_CODE_POINTS,
                        ) +
                        "…"
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

                Then("메타 묶음 경계를 건너뛰지 않고 이어진다") {
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
                                    order = SearchOrder.LATEST,
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

        Given("본문만 일치하고 제목이 검색어 일부와 겹치는 링크가 더 있을 때") {
            lateinit var fixture: ContentSearchFixture

            beforeTest {
                fixture = insertContentSearchFixture(withNearMiss = true)
            }

            When("similar 정렬로 검색하면") {
                val links = fixture
                val pages = allPages(ownerId = links.ownerId, order = "similar")

                Then("제목 점수가 높아도 본문만 일치하면 메타 일치 묶음 뒤에 온다") {
                    pages.flatten() shouldBe
                        listOf(
                            links.titleOnly,
                            links.titleAndBody,
                            links.memoOnly,
                            links.nearMiss,
                            links.shortBodyOnly,
                            links.bodyOnly,
                        )
                }
            }
        }

        Given("메타 묶음 안과 묶음 경계 양쪽에 createdAt이 같은 링크들") {
            lateinit var fixture: ContentSearchFixture

            beforeTest {
                fixture =
                    insertContentSearchFixture(
                        titleOnlyHours = 3,
                        memoOnlyHours = 3,
                        titleAndBodyHours = 2,
                        bodyOnlyHours = 2,
                        shortBodyOnlyHours = 1,
                    )
            }

            When("latest로 한 개씩, 두 개씩 끝까지 이어 읽으면") {
                val links = fixture
                val bySize =
                    listOf(1, 2).associateWith { size ->
                        allPages(ownerId = links.ownerId, order = "latest", size = size).flatten()
                    }

                Then("동률을 id로 나눠 누락·중복 없이 메타 묶음 다음 본문 묶음 순서로 이어진다") {
                    bySize.values.forEach { ids ->
                        ids shouldBe listOf(links.memoOnly, links.titleOnly, links.titleAndBody, links.bodyOnly, links.shortBodyOnly)
                    }
                }
            }
        }

        Given("대소문자만 다른 제목의 링크들") {
            var ownerId = 0L
            lateinit var lowerTitled: Link
            lateinit var upperTitled: Link

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                ownerId = owner.id!!

                suspend fun insertLink(title: String): Link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            title = title,
                            url = "https://example.com/${RandomFixture.randomId()}",
                            summary = null,
                            memo = null,
                            tags = emptyList(),
                        ),
                    )

                lowerTitled = insertLink(title = "$CASE_KEYWORD 정리")
                upperTitled = insertLink(title = "${CASE_KEYWORD.uppercase()} 정리")
            }

            When("소문자 검색어로 찾으면") {
                val ids = listOf(lowerTitled.id!!, upperTitled.id!!)
                val response =
                    getLinksService.getLinks(
                        loginId = ownerId,
                        query = CASE_KEYWORD,
                        folderId = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(),
                    )

                Then("대문자 제목도 TITLE로 일치한다") {
                    response.contents.filter { it.id in ids }.map { it.matchedFields } shouldBe
                        listOf(listOf(MatchedField.TITLE), listOf(MatchedField.TITLE))
                }
            }

            When("similar로 찾으면") {
                val lowerId = lowerTitled.id!!
                val upperId = upperTitled.id!!
                val response =
                    getLinksService.getLinks(
                        loginId = ownerId,
                        query = CASE_KEYWORD,
                        folderId = null,
                        order = "similar",
                        scrollRequest = ScrollRequest(),
                    )

                Then("두 제목의 점수가 같아 id 역순이다") {
                    response.contents.map { it.id } shouldBe listOf(upperId, lowerId)
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

        Given("_·%·\\가 든 제목·본문과 그 글자 자리에 다른 글자가 든 제목·본문") {
            var ownerId = 0L
            lateinit var titles: Map<String, Long>
            lateinit var bodies: Map<String, Long>

            beforeTest {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                ownerId = owner.id!!
                titles =
                    listOf("뷁a_b", "뷁axb", "뷁50%", "뷁50x", "뷁c\\d", "뷁cd").associateWith { title ->
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
                bodies =
                    listOf("뷁a_b", "뷁axb").associateWith { word ->
                        val link =
                            linkRepository.insert(
                                LinkFixture.createRandomLinkOf(
                                    ownerId = owner.id!!,
                                    url = "https://example.com/${RandomFixture.randomId()}",
                                    summary = null,
                                    memo = null,
                                    tags = emptyList(),
                                ),
                            )
                        linkContentRepository.insertAll(
                            listOf(
                                LinkContentFixture.createCollectedContentOf(
                                    linkId = link.id!!,
                                    text = bodyTextWith(keyword = word, before = 10, after = 200),
                                ),
                            ),
                        )
                        link.id!!
                    }
            }

            When("검색어에 _·%·\\가 있으면") {
                val ids = titles
                val contentIds = bodies
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
                            .associate { it.id to it.matchedFields }
                    }

                Then("그 글자가 실제로 든 링크만 나온다") {
                    results shouldBe
                        mapOf(
                            "뷁a_b" to
                                mapOf(
                                    ids.getValue("뷁a_b") to listOf(MatchedField.TITLE),
                                    contentIds.getValue("뷁a_b") to listOf(MatchedField.CONTENT),
                                ),
                            "뷁50%" to mapOf(ids.getValue("뷁50%") to listOf(MatchedField.TITLE)),
                            "뷁c\\d" to mapOf(ids.getValue("뷁c\\d") to listOf(MatchedField.TITLE)),
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

        suspend fun insertSortLinksOf(
            ownerId: Long,
            createdAts: List<Instant>,
            titles: List<String> = createdAts.map { "${RandomFixture.randomSentenceWithMax(20)} ${RandomFixture.randomId()}" },
        ): List<Long> =
            createdAts.zip(titles).map { (createdAt, title) ->
                linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = ownerId, title = title, createdAt = createdAt)).id!!
            }

        suspend fun listLinks(
            ownerId: Long,
            orderKey: String? = null,
            order: String? = null,
            cursor: String? = null,
            size: Int = 50,
            query: String? = null,
        ) = getLinksService.getLinks(
            loginId = ownerId,
            query = query,
            folderId = null,
            orderKey = orderKey,
            order = order,
            scrollRequest = ScrollRequest(cursor = cursor, size = size),
        )

        suspend fun allLinkPages(
            ownerId: Long,
            orderKey: String? = null,
            order: String? = null,
            size: Int,
        ): List<List<Long>> {
            val pages = mutableListOf<List<Long>>()
            var cursor: String? = null
            do {
                val page = listLinks(ownerId = ownerId, orderKey = orderKey, order = order, cursor = cursor, size = size)
                pages += page.contents.map { it.id }
                cursor = page.nextCursor
            } while (cursor != null)
            return pages
        }

        suspend fun insertSortOwner(): Long = userRepository.insert(UserFixture.createRandomValidUser()).id!!

        Given("로그인 사용자의 저장일·제목이 서로 다른 링크 여러 개") {
            suspend fun insertSortFixture(): Pair<Long, List<Long>> {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val ids = insertSortLinksOf(ownerId = ownerId, createdAts = listOf(3, 0, 4, 1, 2).map { base + it.hours })

                return ownerId to listOf(ids[2], ids[0], ids[4], ids[3], ids[1])
            }

            When("정렬 없이 조회하면") {
                val (ownerId, createdAtDesc) = insertSortFixture()
                val actual = listLinks(ownerId = ownerId)
                val legacy = listLinks(ownerId = ownerId, order = "latest")

                Then("저장일 최신순이고 기존 latest와 같은 순서다") {
                    actual.contents.map { it.id } shouldBe createdAtDesc
                    legacy.contents.map { it.id } shouldBe createdAtDesc
                }
            }

            When("저장일 오름차순으로 조회하면") {
                val (ownerId, createdAtDesc) = insertSortFixture()
                val actual = listLinks(ownerId = ownerId, orderKey = "created_at", order = "asc")

                Then("오래된 링크부터 나온다") {
                    actual.contents.map { it.id } shouldBe createdAtDesc.reversed()
                }
            }

            When("제목 오름차순으로 조회하면") {
                val (ownerId, _) = insertSortFixture()
                val actual = listLinks(ownerId = ownerId, orderKey = "title")
                val legacy = listLinks(ownerId = ownerId, order = "laxico")

                Then("기존 laxico와 같은 순서다") {
                    actual.contents.map { it.id } shouldBe legacy.contents.map { it.id }
                }
            }

            When("제목 내림차순으로 조회하면") {
                val (ownerId, _) = insertSortFixture()
                val asc = listLinks(ownerId = ownerId, orderKey = "title", order = "asc")
                val desc = listLinks(ownerId = ownerId, orderKey = "title", order = "desc")

                Then("제목 오름차순의 역순이다") {
                    desc.contents.map { it.id } shouldBe asc.contents.map { it.id }.reversed()
                }
            }

            When("기존 값 latest·earliest·similar로 조회하면") {
                val (ownerId, _) = insertSortFixture()
                val legacy =
                    listOf("latest", "earliest", "similar").map {
                        listLinks(ownerId = ownerId, order = it).contents.map { c ->
                            c.id
                        }
                    }
                val current =
                    listOf("created_at" to "desc", "created_at" to "asc", "similarity" to "desc").map { (orderKey, order) ->
                        listLinks(ownerId = ownerId, orderKey = orderKey, order = order).contents.map { it.id }
                    }

                Then("대응하는 기준·방향으로 조회한 것과 같은 순서다") {
                    legacy shouldBe current
                }
            }

            When("검색어 없이 유사도로 조회하면") {
                val (ownerId, createdAtDesc) = insertSortFixture()
                val actual = listLinks(ownerId = ownerId, orderKey = "similarity")

                Then("id 내림차순이다") {
                    actual.contents.map { it.id } shouldBe createdAtDesc.sortedDescending()
                }
            }

            When("검색어와 함께 제목 내림차순으로 조회하면") {
                val links = insertContentSearchFixture()
                val metadata = setOf(links.titleOnly, links.memoOnly, links.titleAndBody)
                val asc =
                    listLinks(
                        ownerId = links.ownerId,
                        query = SEARCH_KEYWORD,
                        orderKey = "title",
                        order = "asc",
                    ).contents.map { it.id }
                val desc =
                    listLinks(
                        ownerId = links.ownerId,
                        query = SEARCH_KEYWORD,
                        orderKey = "title",
                        order = "desc",
                    ).contents.map { it.id }

                Then("메타데이터가 일치한 링크가 먼저 오고 그 안에서 제목 내림차순이다") {
                    desc.take(metadata.size).toSet() shouldBe metadata
                    desc shouldBe asc.filter { it in metadata }.reversed() + asc.filterNot { it in metadata }.reversed()
                }
            }
        }

        Given("같은 제목의 링크들과 같은 저장일의 링크들") {
            When("제목 오름차순·내림차순으로 조회하면") {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val sameTitle = "${RandomFixture.randomSentenceWithMax(20)} ${RandomFixture.randomId()}"
                val sameTitleIds =
                    insertSortLinksOf(
                        ownerId = ownerId,
                        createdAts = listOf(2, 0, 1).map { base + it.hours },
                        titles = List(3) { sameTitle },
                    )
                insertSortLinksOf(ownerId = ownerId, createdAts = listOf(base, base))
                val asc = listLinks(ownerId = ownerId, orderKey = "title", order = "asc").contents.map { it.id }
                val desc = listLinks(ownerId = ownerId, orderKey = "title", order = "desc").contents.map { it.id }

                Then("같은 제목끼리는 id가 각각 오름차순·내림차순이다") {
                    asc.filter { it in sameTitleIds } shouldBe sameTitleIds.sorted()
                    desc.filter { it in sameTitleIds } shouldBe sameTitleIds.sortedDescending()
                }
            }

            When("저장일 오름차순·내림차순으로 조회하면") {
                val ownerId = insertSortOwner()
                val createdAt = RandomFixture.randomPastInstant()
                val sameCreatedAtIds = insertSortLinksOf(ownerId = ownerId, createdAts = List(3) { createdAt })
                val asc = listLinks(ownerId = ownerId, orderKey = "created_at", order = "asc").contents.map { it.id }
                val desc = listLinks(ownerId = ownerId, orderKey = "created_at", order = "desc").contents.map { it.id }

                Then("같은 저장일끼리는 id가 각각 오름차순·내림차순이다") {
                    asc shouldBe sameCreatedAtIds.sorted()
                    desc shouldBe sameCreatedAtIds.sortedDescending()
                }
            }
        }

        Given("한 페이지보다 많은 링크") {
            When("제목 내림차순으로 다음 커서를 따라 끝까지 조회하면") {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val titleA = "a ${RandomFixture.randomId()}"
                val titleB = "b ${RandomFixture.randomId()}"
                insertSortLinksOf(
                    ownerId = ownerId,
                    createdAts = List(5) { base + it.hours },
                    titles = listOf(titleA, titleB, titleA, titleB, "c ${RandomFixture.randomId()}"),
                )
                val expected = listLinks(ownerId = ownerId, orderKey = "title", order = "desc").contents.map { it.id }
                val pages = listOf(1, 2, 3).map { allLinkPages(ownerId = ownerId, orderKey = "title", order = "desc", size = it) }

                Then("페이지 경계에 같은 제목이 걸려도 겹치거나 빠지는 링크가 없다") {
                    pages.forEach { it.flatten() shouldBe expected }
                }
            }

            When("저장일 오름차순으로 다음 커서를 따라 끝까지 조회하면") {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val ids = insertSortLinksOf(ownerId = ownerId, createdAts = listOf(2, 0, 2, 1, 3).map { base + it.hours })
                val lastPage = listLinks(ownerId = ownerId, orderKey = "created_at", order = "asc", cursor = null, size = 5)
                val pages = allLinkPages(ownerId = ownerId, orderKey = "created_at", order = "asc", size = 2)

                Then("겹치거나 빠지는 링크가 없고 마지막 페이지는 다음 커서가 없다") {
                    pages.flatten() shouldBe listOf(ids[1], ids[3], ids[0], ids[2], ids[4])
                    lastPage.hasNext shouldBe false
                    lastPage.nextCursor shouldBe null
                }
            }

            When("기존 형식 LATEST 커서(생성 시각 없음)로 저장일 내림차순을 조회하면") {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val ids = insertSortLinksOf(ownerId = ownerId, createdAts = listOf(3, 0, 4, 1, 2).map { base + it.hours })
                val cursor = createCursor(order = SearchOrder.LATEST, value = LinkSearchCursorValue(id = ids[0]))
                val actual = listLinks(ownerId = ownerId, orderKey = "created_at", order = "desc", cursor = cursor)

                Then("커서 링크 다음부터 이어진다") {
                    actual.contents.map { it.id } shouldBe listOf(ids[4], ids[3], ids[1])
                }
            }

            When("기존 형식 LAXICO 커서로 제목 오름차순을 조회하면") {
                val ownerId = insertSortOwner()
                insertSortLinksOf(ownerId = ownerId, createdAts = List(5) { RandomFixture.randomPastInstant() })
                val expected = listLinks(ownerId = ownerId, orderKey = "title").contents
                val cursor =
                    createCursor(order = SearchOrder.LAXICO, value = LinkSearchCursorValue(id = expected[1].id, title = expected[1].title))
                val actual = listLinks(ownerId = ownerId, orderKey = "title", cursor = cursor)

                Then("커서 링크 다음부터 이어진다") {
                    actual.contents.map { it.id } shouldBe expected.drop(2).map { it.id }
                }
            }

            When("저장일 내림차순 새 커서로 기존 값 latest를 조회하면") {
                val ownerId = insertSortOwner()
                val base = RandomFixture.randomPastInstant()
                val ids = insertSortLinksOf(ownerId = ownerId, createdAts = listOf(3, 0, 4, 1, 2).map { base + it.hours })
                val firstPage = listLinks(ownerId = ownerId, orderKey = "created_at", order = "desc", size = 2)
                val actual = listLinks(ownerId = ownerId, order = "latest", cursor = firstPage.nextCursor)

                Then("커서 링크 다음부터 이어진다") {
                    firstPage.contents.map { it.id } shouldBe listOf(ids[2], ids[0])
                    actual.contents.map { it.id } shouldBe listOf(ids[4], ids[3], ids[1])
                }
            }

            When("제목 커서에 제목 값이 없으면") {
                val ownerId = insertSortOwner()
                val cursor =
                    Base64CursorCodec.encode(
                        LinkSearchCursor(
                            orderKey = LinkOrderKey.TITLE,
                            direction = SortDirection.ASC,
                            value = LinkSearchCursorValue(id = RandomFixture.randomId()),
                        ),
                    )
                val get = suspend { listLinks(ownerId = ownerId, orderKey = "title", cursor = cursor) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }
        }

        suspend fun insertSortOrderLinks(
            ownerId: Long,
            titles: List<String>,
        ): Map<String, Long> {
            val shuffled = titles.shuffled()
            val ids =
                insertSortLinksOf(ownerId = ownerId, createdAts = shuffled.map { RandomFixture.randomPastInstant() }, titles = shuffled)
            return shuffled.zip(ids).toMap()
        }

        Given("한글만 있는 제목, 한글 뒤에 괄호 속 라틴 문자가 붙은 제목, 대소문자가 섞인 라틴 제목, 숫자로 시작하는 제목, 기호로 시작하는 제목의 링크들") {
            When("제목 오름차순으로 조회하면") {
                val ownerId = insertSortOwner()
                insertSortOrderLinks(ownerId = ownerId, titles = SORT_ORDER_TITLES)
                val actual = listLinks(ownerId = ownerId, orderKey = "title", order = "asc")

                Then("기호 → 숫자 → 한글 → 라틴 → 가나 → 한자 → 그 밖 순이고 섞인 제목은 첫 글자부터, 숫자는 숫자로 비교한 자리에 있다") {
                    actual.contents.map { it.title } shouldBe SORT_ORDER_TITLES
                }
            }

            When("대소문자만 다른 제목이 함께 있을 때 제목 오름차순으로 조회하면") {
                val ownerId = insertSortOwner()
                insertSortOrderLinks(ownerId = ownerId, titles = SORT_ORDER_TITLES + CASE_ONLY_TITLE)
                val actual = listLinks(ownerId = ownerId, orderKey = "title", order = "asc").contents.map { it.title }

                Then("대소문자만 다른 두 제목이 서로 붙어 있다") {
                    actual.indexOf(CASE_ONLY_TITLE) - actual.indexOf(CASE_ONLY_TITLE.lowercase()) shouldBe 1
                }
            }

            When("제목 내림차순으로 조회하면") {
                val ownerId = insertSortOwner()
                insertSortOrderLinks(ownerId = ownerId, titles = SORT_ORDER_TITLES)
                val actual = listLinks(ownerId = ownerId, orderKey = "title", order = "desc")

                Then("제목 오름차순의 정확한 역순이다") {
                    actual.contents.map { it.title } shouldBe SORT_ORDER_TITLES.reversed()
                }
            }

            When("기존 laxico로 조회하면") {
                val ownerId = insertSortOwner()
                insertSortOrderLinks(ownerId = ownerId, titles = SORT_ORDER_TITLES)
                val actual = listLinks(ownerId = ownerId, order = "laxico")

                Then("제목 오름차순과 같은 순서다") {
                    actual.contents.map { it.title } shouldBe SORT_ORDER_TITLES
                }
            }

            When("검색어와 함께 제목 오름차순으로 조회하면") {
                val ownerId = insertSortOwner()
                val titles = SORT_ORDER_TITLES.map { "$SEARCH_KEYWORD $it" }
                val idByTitle = insertSortOrderLinks(ownerId = ownerId, titles = titles)
                val bodyOnly = insertSortLinksOf(ownerId = ownerId, createdAts = listOf(RandomFixture.randomPastInstant())).single()
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createCollectedContentOf(
                            linkId = bodyOnly,
                            text = bodyTextWith(keyword = SEARCH_KEYWORD, before = 40, after = 300),
                        ),
                    ),
                )
                val actual = listLinks(ownerId = ownerId, query = SEARCH_KEYWORD, orderKey = "title", order = "asc")

                Then("메타데이터가 일치한 링크가 먼저 오고 그 안에서 한국어 사전순이다") {
                    actual.contents.map { it.id } shouldBe titles.map { idByTitle.getValue(it) } + bodyOnly
                }
            }
        }

        Given("한 페이지보다 많은, 문자 종류가 섞인 제목의 링크들(같은 제목 포함)") {
            val titles = SORT_ORDER_TITLES.flatMap { if (it in SORT_ORDER_DUPLICATES) listOf(it, it) else listOf(it) }

            When("제목 오름차순으로 다음 커서를 따라 끝까지 조회하면") {
                val ownerId = insertSortOwner()
                insertSortLinksOf(
                    ownerId = ownerId,
                    createdAts = titles.map { RandomFixture.randomPastInstant() },
                    titles = titles.shuffled(),
                )
                val expected = listLinks(ownerId = ownerId, orderKey = "title", order = "asc").contents
                val pages = listOf(1, 2, 3).map { allLinkPages(ownerId = ownerId, orderKey = "title", order = "asc", size = it) }

                Then("페이지 크기와 상관없이 이어 붙인 결과가 한 번에 조회한 순서와 같고 빠지거나 겹친 링크가 없다") {
                    expected.map { it.title } shouldBe titles
                    pages.forEach { it.flatten() shouldBe expected.map { link -> link.id } }
                }
            }

            When("제목 내림차순으로 다음 커서를 따라 끝까지 조회하면") {
                val ownerId = insertSortOwner()
                insertSortLinksOf(
                    ownerId = ownerId,
                    createdAts = titles.map { RandomFixture.randomPastInstant() },
                    titles = titles.shuffled(),
                )
                val expected = listLinks(ownerId = ownerId, orderKey = "title", order = "desc").contents
                val pages = listOf(1, 2, 3).map { allLinkPages(ownerId = ownerId, orderKey = "title", order = "desc", size = it) }

                Then("페이지 크기와 상관없이 이어 붙인 결과가 한 번에 조회한 순서와 같고 빠지거나 겹친 링크가 없다") {
                    expected.map { it.title } shouldBe titles.reversed()
                    pages.forEach { it.flatten() shouldBe expected.map { link -> link.id } }
                }
            }
        }

        Given("다른 사용자의 링크(공유 폴더 아님)가 함께 있을 때") {
            When("어떤 기준·방향으로 조회해도") {
                val ownerId = insertSortOwner()
                val mine = insertSortLinksOf(ownerId = ownerId, createdAts = List(3) { RandomFixture.randomPastInstant() })
                insertSortLinksOf(ownerId = insertSortOwner(), createdAts = List(3) { RandomFixture.randomPastInstant() })
                val seen =
                    listOf("created_at" to "asc", "created_at" to "desc", "title" to "asc", "title" to "desc", "similarity" to "desc")
                        .map { (orderKey, order) ->
                            listLinks(ownerId = ownerId, orderKey = orderKey, order = order).contents.map { it.id }.toSet()
                        }

                Then("다른 사용자의 링크는 나오지 않는다") {
                    seen.forEach { it shouldBe mine.toSet() }
                }
            }
        }
    })

private fun createCursor(
    order: SearchOrder,
    value: LinkSearchCursorValue,
): String = Base64CursorCodec.encode(LinkSearchCursor(order = order, value = value))

private const val SEARCH_KEYWORD = "뷁햏쀍뷁"

private val SORT_ORDER_TITLES =
    listOf(
        "㈜아이센스",
        "1인 앱 개발",
        "2026 회고",
        "가나다",
        "네이버",
        "네이버(NAVER) 검색",
        "이기적 영진닷컴",
        "이벤터스(Event-us)",
        "파일2",
        "파일10",
        "하네스",
        "apple",
        "Apple Developer",
        "Banana",
        "cherry",
        "Zebra",
        "とうきょう",
        "トマト",
        "北京",
        "東京タワー",
        "Москва",
    )

private val SORT_ORDER_DUPLICATES = setOf("네이버", "apple", "北京")

private const val CASE_ONLY_TITLE = "APPLE"

private const val CASE_KEYWORD = "kotlin뷁햏"

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
    val nearMiss: Long?,
    val bodyOnlyText: String,
    val shortBodyLength: Int,
)
