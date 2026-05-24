package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class GetLinksServiceTest :
    BaseServiceTest({
        val getLinksService = koinGet<GetLinksService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        Given("링크 검색 서비스 테스트") {
            lateinit var user: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("최신순으로 검색하면") {
                Then("folderEmoji와 todo preview를 포함해 반환한다") {
                    val firstLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                folderId = folder.id,
                                title = "검색 대상 첫 링크",
                                url = "https://example.com/first",
                                tags = listOf("검색", "첫번째"),
                            ),
                        )
                    val secondLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                title = "검색 대상 둘째 링크",
                                url = "https://example.com/second",
                                tags = listOf("검색", "둘째"),
                            ),
                        )
                    todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = secondLink.id!!, ownerId = user.id!!, title = "todo-1"))
                    todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = secondLink.id!!, ownerId = user.id!!, title = "todo-2"))
                    todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = secondLink.id!!, ownerId = user.id!!, title = "todo-3"))
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
                    response.contents[0].folderEmoji shouldBe null
                    response.contents[0].todos.shouldHaveSize(2)
                    response.contents[0].countMoreTodos shouldBe 1
                    response.contents[1].id shouldBe firstLink.id
                    response.contents[1].folderId shouldBe folder.id
                    response.contents[1].folderName shouldBe folder.name
                    response.contents[1].folderEmoji shouldBe folder.emoji
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
