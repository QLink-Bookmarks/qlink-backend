package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchCursorValue
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.GetFoldersContentResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.time.Duration.Companion.hours
import kotlin.time.toKotlinInstant

class GetFoldersServiceTest :
    BaseServiceTest({
        val getFoldersService = koinGet<GetFoldersService>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val tx = koinGet<com.qlink.common.transaction.TransactionRunner>()
        val userRepository = koinGet<UserRepository>()

        Given("한글·라틴·숫자가 섞인 이름의 폴더들") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                SORT_ORDER_FOLDER_NAMES.shuffled().forEach {
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = it))
                }
            }

            suspend fun getLaxico(
                cursor: String? = null,
                size: Int = 20,
            ): ScrollResponse<GetFoldersContentResponse> =
                getFoldersService.getFolders(
                    loginId = user.id!!,
                    query = null,
                    order = "laxico",
                    scrollRequest = ScrollRequest(cursor = cursor, size = size),
                )

            suspend fun allLaxicoPages(size: Int): List<List<Long>> {
                val pages = mutableListOf<List<Long>>()
                var cursor: String? = null
                do {
                    val page = getLaxico(cursor = cursor, size = size)
                    pages += page.contents.map { it.id }
                    cursor = page.nextCursor
                } while (cursor != null)
                return pages
            }

            When("laxico로 조회하면") {
                val get = suspend { getLaxico() }

                Then("이름이 한국어 사전순이다") {
                    get().contents.map { it.name } shouldBe SORT_ORDER_FOLDER_NAMES
                }
            }

            When("laxico 커서로 다음 페이지를 끝까지 조회하면") {
                val get = suspend { getLaxico().contents to listOf(1, 2, 3).map { allLaxicoPages(size = it) } }

                Then("이어 붙인 결과가 한 번에 조회한 순서와 같고 빠지거나 겹친 폴더가 없다") {
                    val (expected, pages) = get()

                    expected.map { it.name } shouldBe SORT_ORDER_FOLDER_NAMES
                    pages.forEach { it.flatten() shouldBe expected.map { folder -> folder.id } }
                }
            }
        }

        Given("폴더 목록 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("본인 소유 폴더를 조회하면") {
                lateinit var sharedFolder: Folder

                beforeTest {
                    val memberOne = userRepository.insert(UserFixture.createRandomValidUser())
                    val memberTwo = userRepository.insert(UserFixture.createRandomValidUser())
                    sharedFolder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(
                                ownerId = user.id!!,
                                name = "내 공유 폴더",
                                sharedAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
                            ),
                        )
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = otherUser.id!!, name = "다른 사람 폴더"))
                    tx.required {
                        FolderMembers.insert {
                            it[folderId] = sharedFolder.id!!
                            it[userId] = memberOne.id!!
                            it[userName] = memberOne.nickname
                            it[role] = "member"
                        }
                        FolderMembers.insert {
                            it[folderId] = sharedFolder.id!!
                            it[userId] = memberTwo.id!!
                            it[userName] = memberTwo.nickname
                            it[role] = "member"
                        }
                    }
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = sharedFolder.id))
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = sharedFolder.id))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("본인 폴더만 카운트와 함께 반환한다") {
                    val response = get()

                    response.isEmpty shouldBe false
                    response.contents.shouldHaveSize(1)
                    response.contents.first().id shouldBe sharedFolder.id
                    response.contents.first().name shouldBe sharedFolder.name
                    response.contents.first().emoji shouldBe sharedFolder.emoji
                    response.contents.first().ownerNickname shouldBe null
                    response.contents.first().isShared shouldBe true
                    response.contents.first().shareCounts shouldBe 3
                    response.contents.first().linkCounts shouldBe 2
                    response.contents.first().myLinkCounts shouldBe 2
                }
            }

            When("query로 폴더명을 검색하면") {
                beforeTest {
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "업무 폴더"))
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "개인 보관함"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "업무",
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("이름 검색어로 필터링한다") {
                    val response = get()

                    response.contents.shouldHaveSize(1)
                    response.contents.first().name shouldBe "업무 폴더"
                }
            }

            When("latest 순으로 조회하면") {
                lateinit var first: Folder
                lateinit var second: Folder

                beforeTest {
                    first = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "latest a"))
                    second = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "latest b"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "latest",
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("id 내림차순으로 반환한다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe listOf(second.id, first.id)
                }
            }

            When("earliest 순으로 조회하면") {
                lateinit var first: Folder
                lateinit var second: Folder

                beforeTest {
                    first = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "earliest a"))
                    second = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "earliest b"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "earliest",
                            order = "earliest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("id 오름차순으로 반환한다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe listOf(first.id, second.id)
                }
            }

            When("order가 비어 있으면") {
                lateinit var first: Folder
                lateinit var second: Folder

                beforeTest {
                    first = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "default order a"))
                    second = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "default order b"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "default order",
                            order = "",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("기본 최신순으로 조회한다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe listOf(second.id, first.id)
                }
            }

            When("laxico 순으로 조회하면") {
                beforeTest {
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "gamma"))
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "alpha"))
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "beta"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "laxico",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("name 오름차순으로 반환한다") {
                    val response = get()

                    response.contents.map { it.name } shouldBe listOf("alpha", "beta", "gamma")
                }
            }

            When("similar 순으로 조회하면") {
                beforeTest {
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "xxyy"))
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "xx"))
                    folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "zzxxyy"))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "xx",
                            order = "similar",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("유사도 높은 순으로 반환한다") {
                    val response = get()

                    response.contents.shouldHaveSize(3)
                    response.contents.first().name shouldBe "xx"
                }
            }

            When("latest 커서로 다음 페이지를 조회하면") {
                lateinit var first: Folder
                lateinit var second: Folder
                lateinit var third: Folder

                beforeTest {
                    first = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "cursor a"))
                    second = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "cursor b"))
                    third = folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = "cursor c"))
                }

                val get =
                    suspend {
                        val firstPage =
                            getFoldersService.getFolders(
                                loginId = user.id!!,
                                query = "cursor",
                                order = "latest",
                                scrollRequest = ScrollRequest(size = 2),
                            )
                        val secondPage =
                            getFoldersService.getFolders(
                                loginId = user.id!!,
                                query = "cursor",
                                order = "latest",
                                scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 2),
                            )

                        firstPage to secondPage
                    }

                Then("다음 페이지를 이어서 반환한다") {
                    val (firstPage, secondPage) = get()

                    firstPage.hasNext shouldBe true
                    firstPage.contents.map { it.id } shouldBe listOf(third.id, second.id)
                    secondPage.hasNext shouldBe false
                    secondPage.contents.map { it.id } shouldBe listOf(first.id)
                }
            }

            When("size가 0 이하이면") {
                beforeTest {
                    repeat(16) { index ->
                        folderRepository.insert(
                            FolderFixture.createFolderWith(
                                ownerId = user.id!!,
                                name = "default size $index",
                            ),
                        )
                    }
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "default size",
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 0),
                        )
                    }

                Then("기본 스크롤 크기 15를 사용한다") {
                    val response = get()

                    response.contents.shouldHaveSize(15)
                    response.hasNext shouldBe true
                    response.nextCursor.shouldNotBeNull()
                }
            }

            When("접근 가능한 폴더가 없으면") {
                lateinit var unrelatedFolder: Folder

                beforeTest {
                    unrelatedFolder = folderRepository.insert(FolderFixture.createFolderWith(ownerId = otherUser.id!!))
                }

                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("빈 목록을 반환한다") {
                    val response = get()

                    response.isEmpty shouldBe true
                    response.contents shouldHaveSize 0
                    response.nextCursor shouldBe null
                    response.hasNext shouldBe false
                    folderRepository.findAllByOwnerId(user.id!!) shouldHaveSize 0
                    folderRepository.findAllByOwnerId(otherUser.id!!).map { it.id } shouldBe listOf(unrelatedFolder.id)
                    folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(unrelatedFolder.id!!) shouldHaveSize 0
                    folderRepository.search(
                        ownerId = user.id!!,
                        query = null,
                        order = FolderSearchOrder.LATEST,
                        cursor = null,
                        size = 10,
                    ) shouldHaveSize 0
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = RandomFixture.randomId(),
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }

            When("order가 잘못되면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "wrong",
                            scrollRequest = ScrollRequest(size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        get()
                    }
                }
            }

            When("cursor 형식이 잘못되면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = "not-base64", size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }

            When("cursor의 order가 요청 order와 다르면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.EARLIEST,
                                    value = FolderSearchCursorValue(id = RandomFixture.randomId()),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        get()
                    }
                }
            }

            When("latest cursor에 id가 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.LATEST,
                                    value = FolderSearchCursorValue(name = "folder"),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("earliest cursor에 id가 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.EARLIEST,
                                    value = FolderSearchCursorValue(name = "folder"),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "earliest",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("laxico cursor에 name이 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.LAXICO,
                                    value = FolderSearchCursorValue(id = RandomFixture.randomId()),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "laxico",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("laxico cursor에 id가 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.LAXICO,
                                    value = FolderSearchCursorValue(name = "folder"),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = null,
                            order = "laxico",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("similar cursor에 score가 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.SIMILAR,
                                    value = FolderSearchCursorValue(id = RandomFixture.randomId(), name = "folder"),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "folder",
                            order = "similar",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("similar cursor에 id가 없으면") {
                val get =
                    suspend {
                        val cursor =
                            Base64CursorCodec.encode(
                                FolderSearchCursor(
                                    order = FolderSearchOrder.SIMILAR,
                                    value = FolderSearchCursorValue(name = "folder", score = 0.9),
                                ),
                            )
                        getFoldersService.getFolders(
                            loginId = user.id!!,
                            query = "folder",
                            order = "similar",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 10),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }
        }

        Given("생성 시각과 id 순서가 다른 내 폴더들(가져온 옛 폴더 포함)") {
            lateinit var user: User
            lateinit var folders: List<Folder>

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val base = RandomFixture.randomPastInstant()
                folders =
                    listOf(
                        FolderFixture.createFolderWith(ownerId = user.id!!, createdAt = base + 3.hours),
                        FolderFixture.createImportedFolderOf(ownerId = user.id!!, createdAt = base),
                        FolderFixture.createFolderWith(ownerId = user.id!!, createdAt = base + 1.hours),
                        FolderFixture.createFolderWith(ownerId = user.id!!, createdAt = base + 1.hours),
                    ).map { folderRepository.insert(it) }
            }

            suspend fun storedLatestOrder(): List<Long> =
                folderRepository
                    .findAllByIds(folders.map { it.id!! })
                    .sortedWith(compareByDescending<Folder> { it.createdAt }.thenByDescending { it.id })
                    .map { it.id!! }

            suspend fun getFolders(
                order: String,
                cursor: String? = null,
                size: Int = 10,
            ) = getFoldersService.getFolders(
                loginId = user.id!!,
                query = null,
                order = order,
                scrollRequest = ScrollRequest(cursor = cursor, size = size),
            )

            When("latest로 조회하면") {
                val get = suspend { getFolders(order = "latest") }

                Then("createdAt 내림차순, 같으면 id 내림차순이다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder()
                    response.contents.map { it.id } shouldBe listOf(folders[0].id, folders[3].id, folders[2].id, folders[1].id)
                }
            }

            When("earliest로 조회하면") {
                val get = suspend { getFolders(order = "earliest") }

                Then("createdAt 오름차순, 같으면 id 오름차순이다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder().reversed()
                }
            }

            When("같은 createdAt 폴더가 페이지 경계에 걸쳐 새 커서로 이어 읽으면") {
                val get =
                    suspend {
                        val firstPage = getFolders(order = "latest", size = 2)
                        firstPage to getFolders(order = "latest", cursor = firstPage.nextCursor, size = 2)
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
                        getFolders(
                            order = "latest",
                            cursor =
                                Base64CursorCodec.encode(
                                    FolderSearchCursor(
                                        order = FolderSearchOrder.LATEST,
                                        value = FolderSearchCursorValue(id = folders[3].id),
                                    ),
                                ),
                        )
                    }

                Then("그 id 행의 createdAt 기준으로 이어진다") {
                    val response = get()

                    response.contents.map { it.id } shouldBe storedLatestOrder().dropWhile { it != folders[3].id }.drop(1)
                }
            }

            When("옛 커서의 id 행이 지워졌으면") {
                val get =
                    suspend {
                        val deletedId = folders[3].id!!
                        folderRepository.deleteById(deletedId)
                        getFolders(
                            order = "latest",
                            cursor =
                                Base64CursorCodec.encode(
                                    FolderSearchCursor(order = FolderSearchOrder.LATEST, value = FolderSearchCursorValue(id = deletedId)),
                                ),
                        )
                    }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }
        }

        Given("소유자 member 행 유무가 다른 공유 폴더") {
            lateinit var user: User
            lateinit var folder: Folder

            for (hasOwnerMember in listOf(true, false)) {
                for (otherMemberCount in listOf(0, 2)) {
                    When("owner 행=$hasOwnerMember, 다른 멤버=$otherMemberCount 상태로 조회하면") {
                        beforeTest {
                            user = userRepository.insert(UserFixture.createRandomValidUser())
                            folder = folderRepository.insert(FolderFixture.createSharedFolderOf(ownerId = user.id!!))
                            if (hasOwnerMember) {
                                folderMemberRepository.insertIfAbsent(
                                    FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = user.id!!),
                                )
                            }
                            repeat(otherMemberCount) {
                                val member = userRepository.insert(UserFixture.createRandomValidUser())
                                folderMemberRepository.insertIfAbsent(
                                    FolderMemberFixture.createMember(folderId = folder.id!!, userId = member.id!!),
                                )
                            }
                        }

                        val get =
                            suspend {
                                getFoldersService.getFolders(
                                    loginId = user.id!!,
                                    query = null,
                                    order = "latest",
                                    scrollRequest = ScrollRequest(),
                                )
                            }

                        Then("owner와 member의 실제 사용자 수를 중복 없이 반환하고 member 행을 보존한다") {
                            val membersBefore = folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(folder.id!!)
                            val response = get().contents.single()
                            val membersAfter = folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(folder.id!!)
                            val storedFolder = folderRepository.findById(folder.id!!).shouldNotBeNull()

                            response.shareCounts shouldBe (membersAfter.map { it.userId } + storedFolder.ownerId).toSet().size
                            response.shareCounts shouldBe otherMemberCount + 1
                            membersAfter shouldBe membersBefore
                            membersAfter shouldHaveSize otherMemberCount + if (hasOwnerMember) 1 else 0
                        }
                    }
                }
            }
        }

        Given("공유 해제 후 stale member 행이 남은 개인 폴더") {
            lateinit var owner: User
            lateinit var staleMember: User
            lateinit var folder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                staleMember = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = owner.id!!),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = folder.id!!, userId = staleMember.id!!),
                )
                linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = folder.id))
                repeat(2) {
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = staleMember.id!!, folderId = folder.id))
                }
            }

            When("개인 폴더 소유자가 조회하면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = owner.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(),
                        )
                    }

                Then("shareCounts는 1이고 stale member 행은 그대로다") {
                    val membersBefore = folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(folder.id!!)
                    val response = get().contents.single()
                    val storedLinks = linkRepository.findAllByFolderIds(listOf(folder.id!!))
                    response.shareCounts shouldBe 1
                    response.linkCounts shouldBe storedLinks.size
                    response.myLinkCounts shouldBe storedLinks.count { it.ownerId == owner.id }
                    response.linkCounts shouldBe 3
                    response.myLinkCounts shouldBe 1
                    folderRepository.findById(folder.id!!).shouldNotBeNull().isShared shouldBe false
                    folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(folder.id!!) shouldBe membersBefore
                    membersBefore shouldHaveSize 2
                }
            }

            When("stale member가 조회하면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = staleMember.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(),
                        )
                    }

                Then("현재 공유 폴더가 아니므로 접근 목록에서 제외한다") {
                    get().contents shouldHaveSize 0
                    folderMemberRepository.existsByFolderIdAndUserId(folderId = folder.id!!, userId = staleMember.id!!) shouldBe true
                    folderRepository.findById(folder.id!!).shouldNotBeNull().isShared shouldBe false
                }
            }
        }

        Given("내 링크와 타인 링크의 수가 다른 폴더") {
            for ((myCount, otherCount) in listOf(0 to 0, 1 to 0, 2 to 1, 4 to 5)) {
                When("내 링크 $myCount 개와 타인 링크 $otherCount 개를 조회하면") {
                    lateinit var user: User
                    lateinit var folder: Folder

                    beforeTest {
                        user = userRepository.insert(UserFixture.createRandomValidUser())
                        val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                        folder = folderRepository.insert(FolderFixture.createSharedFolderOf(ownerId = user.id!!))
                        repeat(myCount) {
                            linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = folder.id))
                        }
                        repeat(otherCount) {
                            val linkOwner = if (it % 2 == 0) otherUser else userRepository.insert(UserFixture.createRandomValidUser())
                            linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = linkOwner.id!!, folderId = folder.id))
                        }
                        linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    }

                    val get =
                        suspend {
                            getFoldersService.getFolders(
                                loginId = user.id!!,
                                query = null,
                                order = "latest",
                                scrollRequest = ScrollRequest(),
                            )
                        }

                    Then("같은 DB 링크 집합으로 전체 수와 호출자 소유 수를 집계한다") {
                        val linksBefore = linkRepository.findAllByFolderIds(listOf(folder.id!!))
                        val response = get().contents.single()
                        val linksAfter = linkRepository.findAllByFolderIds(listOf(folder.id!!))

                        response.linkCounts shouldBe linksAfter.size
                        response.myLinkCounts shouldBe linksAfter.count { it.ownerId == user.id }
                        response.linkCounts shouldBe myCount + otherCount
                        response.myLinkCounts shouldBe myCount
                        response.linkCounts - response.myLinkCounts shouldBe otherCount
                        response.myLinkCounts.shouldBeInRange(0..response.linkCounts)
                        linksAfter.map { Triple(it.id, it.ownerId, it.folderId) } shouldBe
                            linksBefore.map { Triple(it.id, it.ownerId, it.folderId) }
                    }
                }
            }
        }

        Given("소유한 링크 수가 다른 폴더 소유자와 현재 멤버") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var folder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createSharedFolderOf(ownerId = owner.id!!))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = folder.id!!, userId = member.id!!),
                )
                repeat(2) {
                    linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = folder.id))
                }
                linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = member.id!!, folderId = folder.id))
            }

            When("폴더 owner가 아닌 멤버로 조회하면") {
                val get =
                    suspend {
                        getFoldersService.getFolders(
                            loginId = member.id!!,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(),
                        )
                    }

                Then("myLinkCounts는 폴더 owner가 아닌 호출자 기준이고 소유권은 보존된다") {
                    val linksBefore = linkRepository.findAllByFolderIds(listOf(folder.id!!))
                    val response = get().contents.single()
                    val storedLinks = linkRepository.findAllByFolderIds(listOf(folder.id!!))

                    response.ownerId shouldBe folderRepository.findById(folder.id!!).shouldNotBeNull().ownerId
                    response.ownerNickname shouldBe userRepository.findById(owner.id!!).shouldNotBeNull().nickname
                    response.linkCounts shouldBe storedLinks.size
                    response.myLinkCounts shouldBe storedLinks.count { it.ownerId == member.id }
                    response.linkCounts shouldBe 3
                    response.myLinkCounts shouldBe 1
                    storedLinks.map { Triple(it.id, it.ownerId, it.folderId) } shouldBe
                        linksBefore.map { Triple(it.id, it.ownerId, it.folderId) }
                }
            }
        }

        Given("소유권 위임 또는 멤버 탈퇴를 완료하여 접근을 잃은 사용자") {
            for (transferred in listOf(true, false)) {
                When("${if (transferred) "위임 후 이전 owner" else "탈퇴한 멤버"}가 폴더 목록을 조회하면") {
                    lateinit var caller: User
                    lateinit var remainingUser: User
                    lateinit var folder: Folder
                    var linkId = 0L

                    beforeTest {
                        caller = userRepository.insert(UserFixture.createRandomValidUser())
                        remainingUser = userRepository.insert(UserFixture.createRandomValidUser())
                        val originalOwner = if (transferred) caller else remainingUser
                        folder = folderRepository.insert(FolderFixture.createSharedFolderOf(ownerId = originalOwner.id!!))
                        folderMemberRepository.insertIfAbsent(
                            FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = originalOwner.id!!),
                        )
                        val originalMember = if (transferred) remainingUser else caller
                        folderMemberRepository.insertIfAbsent(
                            FolderMemberFixture.createMember(folderId = folder.id!!, userId = originalMember.id!!),
                        )
                        linkId = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = caller.id!!, folderId = folder.id)).id!!
                        if (transferred) {
                            folderRepository.update(folder.delegateTo(remainingUser.id!!))
                            folderMemberRepository.updateRole(folderId = folder.id!!, userId = remainingUser.id!!, role = MemberRole.OWNER)
                        }
                        folderMemberRepository.deleteByFolderIdAndUserId(folderId = folder.id!!, userId = caller.id!!)
                    }

                    val get =
                        suspend {
                            getFoldersService.getFolders(
                                loginId = caller.id!!,
                                query = null,
                                order = "latest",
                                scrollRequest = ScrollRequest(),
                            )
                        }

                    Then("폴더는 목록에서 제외되고 기존 자기 링크의 owner와 folder는 유지된다") {
                        get().contents shouldHaveSize 0
                        folderRepository.findById(folder.id!!).shouldNotBeNull().ownerId shouldBe remainingUser.id
                        folderMemberRepository.existsByFolderIdAndUserId(folderId = folder.id!!, userId = caller.id!!) shouldBe false
                        val storedLink = linkRepository.findById(linkId).shouldNotBeNull()
                        storedLink.ownerId shouldBe caller.id
                        storedLink.folderId shouldBe folder.id
                    }
                }
            }
        }

        Given("검색 대상인 소유 폴더와 참여 폴더 및 접근할 수 없는 폴더") {
            lateinit var user: User
            lateinit var folders: List<Folder>
            lateinit var keyword: String

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                keyword = RandomFixture.randomFixedSentence(8)
                val base = RandomFixture.randomPastInstant()
                folders =
                    (0..4).map { index ->
                        val owner = if (index % 2 == 0) user else otherUser
                        val folder =
                            folderRepository.insert(
                                FolderFixture.createFolderWith(
                                    ownerId = owner.id!!,
                                    name = "$keyword $index",
                                    sharedAt = RandomFixture.randomPastInstant(),
                                    createdAt = base + index.hours,
                                ),
                            )
                        folderMemberRepository.insertIfAbsent(
                            FolderMemberFixture.createOwnerMember(folderId = folder.id!!, userId = owner.id!!),
                        )
                        if (owner.id != user.id) {
                            folderMemberRepository.insertIfAbsent(
                                FolderMemberFixture.createMember(folderId = folder.id!!, userId = user.id!!),
                            )
                        }
                        repeat(index) {
                            linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = folder.id))
                        }
                        linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!, folderId = folder.id))
                        folder
                    }
                folderRepository.insert(FolderFixture.createFolderWith(ownerId = otherUser.id!!, name = "$keyword outside"))
                val privateFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(ownerId = otherUser.id!!, name = "$keyword stale"),
                    )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = privateFolder.id!!, userId = user.id!!),
                )
                folderRepository.insert(FolderFixture.createFolderWith(ownerId = user.id!!, name = RandomFixture.randomFixedSentence(7)))
            }

            for (order in FolderSearchOrder.entries) {
                When("검색과 $order 정렬로 여러 페이지를 조회하면") {
                    val get =
                        suspend {
                            val pages = mutableListOf<ScrollResponse<GetFoldersContentResponse>>()
                            var cursor: String? = null
                            do {
                                val page =
                                    getFoldersService.getFolders(
                                        loginId = user.id!!,
                                        query = keyword,
                                        order = order.name.lowercase(),
                                        scrollRequest = ScrollRequest(cursor = cursor, size = 2),
                                    )
                                pages.add(page)
                                cursor = page.nextCursor
                            } while (cursor != null && pages.size < 4)
                            pages
                        }

                    Then("접근 가능한 폴더를 기존 순서로 빠짐·중복 없이 반환하고 모든 count가 DB와 일치한다") {
                        val pages = get()
                        pages shouldHaveSize 3
                        pages.forEachIndexed { index, page ->
                            page.hasNext shouldBe (index < pages.lastIndex)
                            page.hasNext shouldBe (page.nextCursor != null)
                            page.contents shouldHaveSize if (index < pages.lastIndex) 2 else 1
                        }
                        val responses = pages.flatMap { it.contents }
                        val storedFolders = folderRepository.findAllByIds(folders.map { it.id!! })
                        val expectedIds =
                            when (order) {
                                FolderSearchOrder.LATEST -> {
                                    storedFolders
                                        .sortedWith(compareByDescending<Folder> { it.createdAt }.thenByDescending { it.id })
                                        .map { it.id!! }
                                }

                                FolderSearchOrder.EARLIEST -> {
                                    storedFolders
                                        .sortedWith(compareBy<Folder> { it.createdAt }.thenBy { it.id })
                                        .map { it.id!! }
                                }

                                FolderSearchOrder.LAXICO -> {
                                    storedFolders
                                        .sortedWith(compareBy<Folder> { it.name }.thenBy { it.id })
                                        .map { it.id!! }
                                }

                                FolderSearchOrder.SIMILAR -> {
                                    folderRepository
                                        .search(
                                            ownerId = user.id!!,
                                            query = keyword,
                                            order = order,
                                            cursor = null,
                                            size = 10,
                                        ).map { it.id }
                                }
                            }

                        responses.map { it.id } shouldBe expectedIds
                        responses.map { it.id }.toSet() shouldBe storedFolders.map { it.id }.toSet()
                        responses shouldHaveSize storedFolders.size
                        for (response in responses) {
                            val storedLinks = linkRepository.findAllByFolderIds(listOf(response.id))
                            val storedFolder = folderRepository.findById(response.id).shouldNotBeNull()
                            val storedMembers = folderMemberRepository.findAllByFolderIdOrderByJoinedAtAsc(response.id)
                            response.linkCounts shouldBe storedLinks.size
                            response.myLinkCounts shouldBe storedLinks.count { it.ownerId == user.id }
                            response.myLinkCounts.shouldBeInRange(0..response.linkCounts)
                            response.shareCounts shouldBe (storedMembers.map { it.userId } + storedFolder.ownerId).toSet().size
                        }
                    }
                }
            }
        }
    })

private val SORT_ORDER_FOLDER_NAMES =
    listOf("3월 여행", "10월 여행", "가계부", "업무", "하루", "Alpha", "beta", "zoo 폴더", "ノート", "旅行", "Москва")
