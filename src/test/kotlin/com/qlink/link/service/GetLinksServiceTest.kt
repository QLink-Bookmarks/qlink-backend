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
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.support.truncatedToSecond
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.toKotlinInstant

class GetLinksServiceTest :
    BaseServiceTest({
        val getLinksService = koinGet<GetLinksService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()

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
                Then("folderEmoji와 todo preview를 포함해 반환한다") {
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
                    response.contents[0].folderEmoji shouldBe null
                    response.contents[0].todos.shouldHaveSize(2)
                    response.contents[0]
                        .todos
                        .first { it.title == "todo-1" }
                        .completedAt
                        .truncatedToSecond() shouldBe
                        completedAt.truncatedToSecond()
                    response.contents[0]
                        .todos
                        .first { it.title == "todo-2" }
                        .completedAt shouldBe null
                    response.contents[0].countMoreTodos shouldBe 1
                    response.contents[1].id shouldBe firstLink.id
                    response.contents[1].status shouldBe firstLink.status
                    response.contents[1].folderId shouldBe folder.id
                    response.contents[1].folderName shouldBe folder.name
                    response.contents[1].folderEmoji shouldBe folder.emoji
                    response.contents[1].workModel shouldBe model.model
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
                            ),
                        )
                    val third =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "folder search c",
                                url = "https://example.com/c",
                            ),
                        )
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
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
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
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
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
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
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
    })

private fun createCursor(
    order: LinkSearchOrder,
    value: LinkSearchCursorValue,
): String = Base64CursorCodec.encode(LinkSearchCursor(order = order, value = value))
