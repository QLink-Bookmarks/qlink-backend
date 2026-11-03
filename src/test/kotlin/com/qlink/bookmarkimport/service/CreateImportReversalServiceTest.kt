@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.ImportRequestFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.ExperimentalUuidApi

class CreateImportReversalServiceTest :
    BaseServiceTest({
        val createImportReversalService = koinGet<CreateImportReversalService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()
        val tx = koinGet<TransactionRunner>()

        suspend fun reversalStateOf(importId: Long): List<Any?> =
            listOf(
                bookmarkImportRepository.findById(importId)?.let { listOf(it.reversedAt, it.updatedAt) },
                bookmarkImportRepository.findLinksByImportId(importId).map { listOf(it.id, it.thumbnailStatus, it.reversedAt) },
                bookmarkImportRepository.findFoldersByImportId(importId).map { listOf(it.id, it.reversedAt) },
            )

        class ImportedItems(
            val bookmarkImport: BookmarkImport,
            val newFolder: Folder,
            val existingFolder: Folder,
            val existingFolderOwnLink: Link,
            val pendingNewFolderLink: Link,
            val doneNewFolderLink: Link,
            val existingFolderLink: Link,
            val unfiledLink: Link,
            val addedLaterLink: Link,
        ) {
            val importedLinks: List<Link>
                get() = listOf(pendingNewFolderLink, doneNewFolderLink, existingFolderLink, unfiledLink)
        }

        suspend fun insertImportedItemsOf(ownerId: Long): ImportedItems {
            val newFolder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = ownerId))
            val existingFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(ownerId))
            val existingFolderOwnLink =
                linkRepository.insert(
                    LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = existingFolder.id),
                )
            val pendingNewFolderLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = newFolder.id))
            val doneNewFolderLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = newFolder.id))
            val existingFolderLink =
                linkRepository.insert(
                    LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = existingFolder.id),
                )
            val unfiledLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = ownerId))
            val bookmarkImport =
                bookmarkImportRepository.insert(ImportFixture.createBookmarkImportOf(ownerId = ownerId, linkCount = 4, folderCount = 1))
            val importFolderIdByFolderId =
                bookmarkImportRepository
                    .insertFolders(
                        listOf(
                            ImportFixture.createImportFolderOf(
                                importId = bookmarkImport.id!!,
                                folderId = newFolder.id!!,
                                kind = ImportFolderKind.NEW,
                                name = newFolder.name,
                            ),
                            ImportFixture.createImportFolderOf(
                                importId = bookmarkImport.id,
                                folderId = existingFolder.id!!,
                                kind = ImportFolderKind.EXISTING,
                                name = existingFolder.name,
                            ),
                        ),
                    ).associate { it.folderId to it.id }
            bookmarkImportRepository.insertLinks(
                listOf(pendingNewFolderLink, doneNewFolderLink, existingFolderLink, unfiledLink).map { link ->
                    ImportFixture.createImportLinkOf(
                        importId = bookmarkImport.id,
                        linkId = link.id!!,
                        importFolderId = link.folderId?.let(importFolderIdByFolderId::getValue),
                        url = link.url,
                        title = link.title,
                        thumbnailStatus = if (link == doneNewFolderLink) ThumbnailStatus.DONE else ThumbnailStatus.PENDING,
                    )
                },
            )
            val addedLaterLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = newFolder.id))

            return ImportedItems(
                bookmarkImport = bookmarkImport,
                newFolder = newFolder,
                existingFolder = existingFolder,
                existingFolderOwnLink = existingFolderOwnLink,
                pendingNewFolderLink = pendingNewFolderLink,
                doneNewFolderLink = doneNewFolderLink,
                existingFolderLink = existingFolderLink,
                unfiledLink = unfiledLink,
                addedLaterLink = addedLaterLink,
            )
        }

        Given("썸네일 작업이 남은 내 가져오기") {
            lateinit var user: User
            lateinit var items: ImportedItems

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                items = insertImportedItemsOf(user.id!!)
            }

            When("링크와 NEW 폴더를 고르면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = items.bookmarkImport.id!!,
                            request =
                                ImportRequestFixture.createReversalRequest(
                                    linkIds = listOf(items.pendingNewFolderLink.id!!, items.doneNewFolderLink.id!!),
                                    folderIds = listOf(items.newFolder.id!!),
                                ),
                        )
                    }

                Then("고른 링크·폴더가 지워지고, 가져오기 링크 행은 reversed_at이 채워지며 PENDING이던 행은 STOPPED, 가져오기 폴더 행·기록의 reversed_at이 채워진다") {
                    reverse()

                    linkRepository.findAllByIds(listOf(items.pendingNewFolderLink.id!!, items.doneNewFolderLink.id!!)).shouldBeEmpty()
                    folderRepository.findById(items.newFolder.id!!) shouldBe null
                    val rowByLinkId = bookmarkImportRepository.findLinksByImportId(items.bookmarkImport.id!!).associateBy { it.linkId }
                    rowByLinkId.getValue(items.pendingNewFolderLink.id!!).reversedAt.shouldNotBeNull()
                    rowByLinkId.getValue(items.pendingNewFolderLink.id!!).thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    rowByLinkId.getValue(items.doneNewFolderLink.id!!).reversedAt.shouldNotBeNull()
                    rowByLinkId.getValue(items.doneNewFolderLink.id!!).thumbnailStatus shouldBe ThumbnailStatus.DONE
                    rowByLinkId.getValue(items.unfiledLink.id!!).reversedAt shouldBe null
                    bookmarkImportRepository
                        .findFoldersByImportId(items.bookmarkImport.id!!)
                        .single { it.folderId == items.newFolder.id }
                        .reversedAt
                        .shouldNotBeNull()
                    bookmarkImportRepository.findById(items.bookmarkImport.id!!)?.reversedAt.shouldNotBeNull()
                }
            }

            When("지우는 폴더 안에 고르지 않은 가져온 링크와 나중에 넣은 링크가 있으면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = items.bookmarkImport.id!!,
                            request = ImportRequestFixture.createReversalRequest(folderIds = listOf(items.newFolder.id!!)),
                        )
                    }

                Then("그 링크들은 지워지지 않고 미분류(folder_id null)가 된다") {
                    reverse()

                    folderRepository.findById(items.newFolder.id!!) shouldBe null
                    linkRepository
                        .findAllByIds(listOf(items.pendingNewFolderLink.id!!, items.doneNewFolderLink.id!!, items.addedLaterLink.id!!))
                        .map { it.id to it.folderId } shouldContainExactlyInAnyOrder
                        listOf(items.pendingNewFolderLink.id to null, items.doneNewFolderLink.id to null, items.addedLaterLink.id to null)
                }
            }

            When("EXISTING 폴더에 들어간 가져온 링크를 고르면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = items.bookmarkImport.id!!,
                            request = ImportRequestFixture.createReversalRequest(linkIds = listOf(items.existingFolderLink.id!!)),
                        )
                    }

                Then("그 링크들만 지워지고 기존 폴더와 그 안의 다른 링크는 그대로다") {
                    reverse()

                    linkRepository.findById(items.existingFolderLink.id!!) shouldBe null
                    folderRepository.findById(items.existingFolder.id!!)?.name shouldBe items.existingFolder.name
                    linkRepository.findAllByFolderIds(listOf(items.existingFolder.id!!)).map { it.id } shouldContainExactlyInAnyOrder
                        listOf(items.existingFolderOwnLink.id)
                }
            }

            When("일부만 되돌리면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = items.bookmarkImport.id!!,
                            request = ImportRequestFixture.createReversalRequest(linkIds = listOf(items.unfiledLink.id!!)),
                        )
                    }

                Then("응답 요약이 PARTIALLY_UNDONE, isReversible=true다") {
                    val summary = reverse()

                    summary.status shouldBe ImportStatus.PARTIALLY_UNDONE
                    summary.isReversible shouldBe true
                    bookmarkImportRepository.findById(items.bookmarkImport.id!!)?.reversedAt.shouldNotBeNull()
                    linkRepository.findById(items.unfiledLink.id!!) shouldBe null
                }
            }

            When("남은 항목을 모두 되돌리면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = items.bookmarkImport.id!!,
                            request =
                                ImportRequestFixture.createReversalRequest(
                                    linkIds = items.importedLinks.map { it.id!! },
                                    folderIds = listOf(items.newFolder.id!!),
                                ),
                        )
                    }

                Then("응답 요약이 UNDONE, isReversible=false, progress=1이다") {
                    val summary = reverse()

                    summary.status shouldBe ImportStatus.UNDONE
                    summary.isReversible shouldBe false
                    summary.progress shouldBe 1.0
                    linkRepository.findAllByIds(items.importedLinks.map { it.id!! }).shouldBeEmpty()
                    bookmarkImportRepository.findLinksByImportId(items.bookmarkImport.id!!).all { it.reversedAt != null } shouldBe true
                }
            }

            When("같은 요청을 다시 보내면") {
                val reverseTwice =
                    suspend {
                        val request =
                            ImportRequestFixture.createReversalRequest(
                                linkIds = listOf(items.pendingNewFolderLink.id!!, items.unfiledLink.id!!),
                                folderIds = listOf(items.newFolder.id!!),
                            )
                        val first = createImportReversalService.createReversal(user.id!!, items.bookmarkImport.id!!, request)
                        val stateAfterFirst = reversalStateOf(items.bookmarkImport.id!!)
                        val second = createImportReversalService.createReversal(user.id!!, items.bookmarkImport.id!!, request)

                        Triple(first to second, stateAfterFirst, reversalStateOf(items.bookmarkImport.id!!))
                    }

                Then("거절 없이 DB 상태와 응답 요약이 처음과 같다") {
                    val (summaries, stateAfterFirst, stateAfterSecond) = reverseTwice()

                    summaries.second shouldBe summaries.first
                    stateAfterSecond shouldBe stateAfterFirst
                }
            }

            When("두 되돌리기를 동시에 보내면") {
                val reverseConcurrently =
                    suspend {
                        CoroutineScope(Dispatchers.IO)
                            .async {
                                val owner = tx.required { userRepository.insert(UserFixture.createRandomValidUser()) }
                                try {
                                    val committed = tx.required { insertImportedItemsOf(owner.id!!) }
                                    val request =
                                        ImportRequestFixture.createReversalRequest(
                                            linkIds = listOf(committed.pendingNewFolderLink.id!!, committed.unfiledLink.id!!),
                                            folderIds = listOf(committed.newFolder.id!!),
                                        )
                                    coroutineScope {
                                        List(2) {
                                            async {
                                                createImportReversalService.createReversal(
                                                    owner.id!!,
                                                    committed.bookmarkImport.id!!,
                                                    request,
                                                )
                                            }
                                        }.awaitAll()
                                    }
                                    tx.readOnly {
                                        val record = bookmarkImportRepository.findById(committed.bookmarkImport.id!!)!!
                                        val rowReversedAts =
                                            bookmarkImportRepository
                                                .findLinksByImportId(committed.bookmarkImport.id)
                                                .mapNotNull { it.reversedAt }
                                                .toSet()
                                        val remaining =
                                            linkRepository.findAllByIds(
                                                listOf(committed.pendingNewFolderLink.id, committed.unfiledLink.id),
                                            )

                                        Triple(record.reversedAt, rowReversedAts, remaining.size)
                                    }
                                } finally {
                                    tx.required { userRepository.deleteById(owner.id!!) }
                                }
                            }.await()
                    }

                Then("둘 다 끝나고 고른 항목이 한 번씩만 지워진다") {
                    val (recordReversedAt, rowReversedAts, remainingLinkCount) = reverseConcurrently()

                    remainingLinkCount shouldBe 0
                    rowReversedAts shouldBe setOf(recordReversedAt)
                }
            }

            When("없는 기록 id면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = user.id!!,
                            importId = RandomFixture.randomId(),
                            request = ImportRequestFixture.createReversalRequest(linkIds = listOf(RandomFixture.randomId())),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_NOT_FOUND.message) {
                        reverse()
                    }
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("되돌리기를 보내면") {
                val reverse =
                    suspend {
                        createImportReversalService.createReversal(
                            loginId = RandomFixture.randomId(),
                            importId = RandomFixture.randomId(),
                            request = ImportRequestFixture.createReversalRequest(linkIds = listOf(RandomFixture.randomId())),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_OWNER_NOT_FOUND.message) {
                        reverse()
                    }
                }
            }
        }
    })
