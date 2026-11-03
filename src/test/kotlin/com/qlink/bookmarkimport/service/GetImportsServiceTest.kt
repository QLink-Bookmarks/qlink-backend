@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.ImportCursorValue
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.bookmarkimport.repository.table.BookmarkImports
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

class GetImportsServiceTest :
    BaseServiceTest({
        val getImportsService = koinGet<GetImportsService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()
        val tx = koinGet<TransactionRunner>()

        suspend fun insertImportOf(
            ownerId: Long,
            createdAt: Instant = Clock.System.now(),
            linkCount: Int = RandomFixture.randomInt(1, 100),
            reversedAt: Instant? = null,
        ): BookmarkImport =
            bookmarkImportRepository
                .insert(ImportFixture.createBookmarkImportOf(ownerId = ownerId, linkCount = linkCount, reversedAt = reversedAt))
                .also { saved ->
                    tx.required {
                        BookmarkImports.update(where = { BookmarkImports.id eq saved.id!! }) {
                            it[BookmarkImports.createdAt] = createdAt.toJavaInstant()
                        }
                    }
                }.let { bookmarkImportRepository.findById(it.id!!)!! }

        suspend fun insertImportsWithTieOf(
            ownerId: Long,
            count: Int,
        ): List<BookmarkImport> {
            val tie = Clock.System.now() - RandomFixture.randomInt(1, 1000).hours

            return List(count) { index ->
                insertImportOf(
                    ownerId = ownerId,
                    createdAt =
                        if (index <
                            2
                        ) {
                            tie
                        } else {
                            tie - (index * 7).hours + RandomFixture.randomInt(0, 6).hours
                        },
                )
            }
        }

        fun List<BookmarkImport>.inListOrder(): List<Long> =
            sortedWith(compareByDescending<BookmarkImport> { it.createdAt }.thenByDescending { it.id }).map { it.id!! }

        Given("나와 다른 사용자의 가져오기 기록들") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                List(2) { insertImportOf(ownerId = otherUser.id!!) }
            }

            When("목록을 조회하면") {
                lateinit var mine: List<BookmarkImport>

                beforeTest {
                    mine = insertImportsWithTieOf(ownerId = user.id!!, count = 16)
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("내 기록만 createdAt 내림차순(같으면 id)으로, 기본 15개다") {
                    val response = get()

                    response.contents.map { it.id } shouldContainExactly mine.inListOrder().take(15)
                    response.hasNext shouldBe true
                }
            }

            When("다음 커서로 이어 읽으면") {
                lateinit var mine: List<BookmarkImport>

                beforeTest {
                    mine = insertImportsWithTieOf(ownerId = user.id!!, count = 5)
                }

                val readAll =
                    suspend {
                        buildList {
                            var page = getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest(size = 2))
                            add(page)
                            while (page.hasNext) {
                                page =
                                    getImportsService.getImports(
                                        loginId = user.id!!,
                                        scrollRequest = ScrollRequest(cursor = page.nextCursor, size = 2),
                                    )
                                add(page)
                            }
                        }
                    }

                Then("빠짐·중복 없이 이어지고 마지막 페이지는 hasNext=false다") {
                    val pages = readAll()

                    pages.flatMap { page -> page.contents.map { it.id } } shouldContainExactly mine.inListOrder()
                    pages.map { it.hasNext } shouldContainExactly listOf(true, true, false)
                }
            }

            When("썸네일 작업 일부가 DONE·FAILED·STOPPED이고 나머지가 PENDING이면") {
                beforeTest {
                    val saved = insertImportOf(ownerId = user.id!!, linkCount = 4)
                    bookmarkImportRepository.insertLinks(
                        listOf(ThumbnailStatus.DONE, ThumbnailStatus.FAILED, ThumbnailStatus.STOPPED, ThumbnailStatus.PENDING).map {
                            ImportFixture.createImportLinkOf(importId = saved.id!!, linkId = RandomFixture.randomId(), thumbnailStatus = it)
                        },
                    )
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("progress는 끝난 행 ÷ link_count다") {
                    get().contents.single().progress shouldBe 0.75
                }
            }

            When("사용자가 가져온 링크와 NEW 폴더를 모두 지웠으면") {
                beforeTest {
                    val folder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = user.id!!))
                    val link = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = folder.id))
                    val saved = insertImportOf(ownerId = user.id!!, linkCount = 1)
                    bookmarkImportRepository.insertFolders(
                        listOf(
                            ImportFixture.createImportFolderOf(importId = saved.id!!, folderId = folder.id!!, kind = ImportFolderKind.NEW),
                        ),
                    )
                    bookmarkImportRepository.insertLinks(
                        listOf(ImportFixture.createImportLinkOf(importId = saved.id, linkId = link.id!!)),
                    )
                    linkRepository.deleteById(link.id)
                    folderRepository.deleteById(folder.id)
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("isReversible=false") {
                    get().contents.single().isReversible shouldBe false
                }
            }

            When("남은 NEW 폴더가 공유되었고 가져온 링크가 모두 지워졌으면") {
                beforeTest {
                    val folder = folderRepository.insert(FolderFixture.createSharedFolderOf(ownerId = user.id!!))
                    val link = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = folder.id))
                    val saved = insertImportOf(ownerId = user.id!!, linkCount = 1)
                    bookmarkImportRepository.insertFolders(
                        listOf(
                            ImportFixture.createImportFolderOf(importId = saved.id!!, folderId = folder.id!!, kind = ImportFolderKind.NEW),
                        ),
                    )
                    bookmarkImportRepository.insertLinks(
                        listOf(ImportFixture.createImportLinkOf(importId = saved.id, linkId = link.id!!)),
                    )
                    linkRepository.deleteById(link.id)
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("isReversible=false") {
                    get().contents.single().isReversible shouldBe false
                }
            }

            When("가져온 링크를 수정(EDITED)만 했으면") {
                beforeTest {
                    val link = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!))
                    val saved = insertImportOf(ownerId = user.id!!, linkCount = 1)
                    bookmarkImportRepository.insertLinks(
                        listOf(
                            ImportFixture.createImportLinkOf(
                                importId = saved.id!!,
                                linkId = link.id!!,
                                url = link.url,
                                title = "${link.title.take(200)} 원래 제목",
                            ),
                        ),
                    )
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("isReversible=true") {
                    get().contents.single().isReversible shouldBe true
                }
            }

            When("일부만 되돌린 기록이면") {
                beforeTest {
                    val reversedAt = RandomFixture.randomPastInstant()
                    val reversedLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!))
                    val remainingLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!))
                    val saved = insertImportOf(ownerId = user.id!!, linkCount = 2, reversedAt = reversedAt)
                    bookmarkImportRepository.insertLinks(
                        listOf(
                            ImportFixture.createImportLinkOf(importId = saved.id!!, linkId = reversedLink.id!!, reversedAt = reversedAt),
                            ImportFixture.createImportLinkOf(importId = saved.id, linkId = remainingLink.id!!),
                        ),
                    )
                }

                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest())
                    }

                Then("PARTIALLY_UNDONE") {
                    get().contents.single().status shouldBe ImportStatus.PARTIALLY_UNDONE
                }
            }

            When("해석할 수 없는 커서면") {
                val get =
                    suspend {
                        getImportsService.getImports(loginId = user.id!!, scrollRequest = ScrollRequest(cursor = "not-a-cursor!"))
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }

            When("커서에 createdAt이 없으면") {
                val get =
                    suspend {
                        getImportsService.getImports(
                            loginId = user.id!!,
                            scrollRequest =
                                ScrollRequest(
                                    cursor = Base64CursorCodec.encode(ImportCursorValue(id = RandomFixture.randomId())),
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }

            When("커서에 id가 없으면") {
                val get =
                    suspend {
                        getImportsService.getImports(
                            loginId = user.id!!,
                            scrollRequest =
                                ScrollRequest(
                                    cursor = Base64CursorCodec.encode(ImportCursorValue(createdAt = Clock.System.now())),
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("목록을 조회하면") {
                val get =
                    suspend {
                        getImportsService.getImports(loginId = RandomFixture.randomId(), scrollRequest = ScrollRequest())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
