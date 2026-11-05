package com.qlink.folder.service

import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.GetFoldersContentResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class FolderSimilarCursorServiceTest :
    BaseServiceTest({
        val service = koinGet<GetFoldersService>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("점수가 같거나 다른 폴더의 SIMILAR 커서 페이지") {
            lateinit var user: User
            lateinit var folders: List<Folder>
            lateinit var keyword: String

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                keyword = RandomFixture.randomFixedSentence(8).lowercase()
                val names =
                    listOf(
                        keyword,
                        "$keyword a",
                        "$keyword b",
                        "$keyword ${RandomFixture.randomFixedSentence(10)}",
                        "$keyword ${RandomFixture.randomFixedSentence(30)}",
                    )
                folders =
                    names.mapIndexed { index, name ->
                        val folder = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = name))
                        repeat(index % 3) {
                            linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = folder.id))
                        }
                        linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!, folderId = folder.id))
                        folder
                    }
                folderRepository.insert(FolderFixture.createFolderWith(ownerId = otherUser.id!!, name = keyword))
            }

            for (hasQuery in listOf(true, false)) {
                When("검색어 유무=$hasQuery 로 SIMILAR 정렬의 다음 페이지를 조회하면") {
                    val get =
                        suspend {
                            val contents = mutableListOf<GetFoldersContentResponse>()
                            var cursor: String? = null
                            var pages = 0
                            do {
                                val page =
                                    service.getFolders(
                                        loginId = user.id!!,
                                        query = if (hasQuery) keyword else null,
                                        order = "similar",
                                        scrollRequest = ScrollRequest(cursor = cursor, size = 2),
                                    )
                                contents.addAll(page.contents)
                                page.hasNext shouldBe (page.nextCursor != null)
                                cursor = page.nextCursor
                                pages++
                            } while (cursor != null && pages < 4)
                            pages shouldBe 3
                            contents
                        }

                    Then("점수와 id 순서로 중복·누락 없이 반환하고 저장된 링크 수를 유지한다") {
                        val actual = get()
                        val storedFolders = folderRepository.findAllByIds(folders.map { it.id!! })
                        val unpaged =
                            folderRepository.search(
                                ownerId = user.id!!,
                                query = if (hasQuery) keyword else null,
                                order = FolderSearchOrder.SIMILAR,
                                cursor = null,
                                size = 10,
                            )
                        if (hasQuery) {
                            unpaged
                                .map { it.score }
                                .distinct()
                                .size
                                .shouldBeGreaterThan(1)
                        } else {
                            unpaged.map { it.score }.distinct() shouldBe listOf(0.0)
                        }
                        actual.map { it.id } shouldBe unpaged.map { it.id }
                        actual.map { it.id }.distinct() shouldHaveSize storedFolders.size
                        actual.map { it.id }.toSet() shouldBe storedFolders.map { it.id }.toSet()
                        for (folder in actual) {
                            val links = linkRepository.findAllByFolderIds(listOf(folder.id))
                            folder.linkCounts shouldBe links.size
                            val counts = unpaged.single { it.id == folder.id }
                            counts.linkCounts shouldBe links.size
                            counts.myLinkCounts shouldBe links.count { it.ownerId == user.id }
                        }
                    }
                }
            }
        }
    })
