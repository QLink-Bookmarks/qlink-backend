package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchCursorValue
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
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
        val tx = koinGet<com.qlink.common.transaction.TransactionRunner>()
        val userRepository = koinGet<UserRepository>()

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
                    response.contents.first().isShared shouldBe true
                    response.contents.first().shareCounts shouldBe 3
                    response.contents.first().linkCounts shouldBe 2
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

            When("폴더가 없으면") {
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
    })
