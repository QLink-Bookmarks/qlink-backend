@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.folder.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.CreateImportReversalRequest
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.bookmarkimport.service.CreateImportReversalService
import com.qlink.bookmarkimport.service.FillImportThumbnailService
import com.qlink.bookmarkimport.service.GetImportDetailService
import com.qlink.bookmarkimport.service.GetImportReversalPreviewService
import com.qlink.common.error.BusinessException
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FakeLinkPreviewSyncClient
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi

class FolderDeletionImportHistoryServiceTest :
    BaseServiceTest({
        val deletion = koinGet<DeleteFolderService>()
        val preview = koinGet<GetImportReversalPreviewService>()
        val reversal = koinGet<CreateImportReversalService>()
        val fill = koinGet<FillImportThumbnailService>()
        val detail = koinGet<GetImportDetailService>()
        val users = koinGet<UserRepository>()
        val folders = koinGet<FolderRepository>()
        val links = koinGet<LinkRepository>()
        val imports = koinGet<BookmarkImportRepository>()
        val client = koinGet<FakeLinkPreviewSyncClient>()

        Given("import snapshot·failure·총계와 preview URL이 있는 무관한 링크") {
            lateinit var owner: User
            lateinit var source: Folder
            lateinit var importedLink: Link
            lateinit var unrelated: Link
            lateinit var bookmarkImport: BookmarkImport
            lateinit var folderRow: ImportFolder
            lateinit var linkRow: ImportLink
            lateinit var failure: ImportFailure
            beforeTest {
                client.reset()
                owner = users.insert(UserFixture.createRandomValidUser())
                source = folders.insert(FolderFixture.createImportedFolderOf(owner.id!!))
                importedLink = links.insert(LinkFixture.createImportedLinkOf(ownerId = owner.id!!, folderId = source.id))
                unrelated =
                    links.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            thumbnailUrl = RandomFixture.randomPreviewUrl(),
                        ),
                    )
                bookmarkImport =
                    imports.insert(
                        ImportFixture.createBookmarkImportOf(
                            ownerId = owner.id!!,
                            linkCount = 1,
                            folderCount = 1,
                            failedCount = 1,
                        ),
                    )
                folderRow =
                    imports
                        .insertFolders(
                            listOf(
                                ImportFixture.createImportFolderOf(
                                    importId = bookmarkImport.id!!,
                                    folderId = source.id!!,
                                    name = source.name,
                                ),
                            ),
                        ).single()
                linkRow =
                    imports
                        .insertLinks(
                            listOf(
                                ImportFixture.createImportLinkSnapshotOf(
                                    importId = bookmarkImport.id!!,
                                    link = importedLink,
                                    importFolderId = folderRow.id,
                                ),
                            ),
                        ).single()
                failure = imports.insertFailures(listOf(ImportFixture.createImportFailureOf(bookmarkImport.id!!))).single()
            }

            suspend fun verifySnapshots() {
                val actual = imports.findById(bookmarkImport.id!!).shouldNotBeNull()
                actual.linkCount shouldBe bookmarkImport.linkCount
                actual.folderCount shouldBe bookmarkImport.folderCount
                actual.skippedCount shouldBe bookmarkImport.skippedCount
                actual.failedCount shouldBe bookmarkImport.failedCount
                actual.fileName shouldBe bookmarkImport.fileName
                actual.reversedAt shouldBe bookmarkImport.reversedAt
                val savedFolder = imports.findFoldersByImportId(bookmarkImport.id!!).single()
                savedFolder.id shouldBe folderRow.id
                savedFolder.name shouldBe folderRow.name
                savedFolder.reversedAt shouldBe null
                val savedLink = imports.findLinkById(linkRow.id!!).shouldNotBeNull()
                savedLink.linkId shouldBe importedLink.id
                savedLink.title shouldBe linkRow.title
                savedLink.url shouldBe linkRow.url
                savedLink.reversedAt shouldBe null
                val savedFailure = imports.findFailuresByImportId(bookmarkImport.id!!).single()
                savedFailure.title shouldBe failure.title
                savedFailure.url shouldBe failure.url
                savedFailure.reason shouldBe failure.reason
                links.findById(unrelated.id!!)?.thumbnailUrl shouldBe unrelated.thumbnailUrl
            }
            When("cascade 후 import snapshot을 조회하면") {
                val delete = suspend { deletion.deleteFolder(loginId = owner.id!!, folderId = source.id!!, onDelete = "cascade") }
                Then("이력·총계는 보존되고 현재 실체만 DELETED로 판정한다") {
                    shouldNotThrow<BusinessException> { delete() }
                    verifySnapshots()
                    val snapshot = detail.getImportDetail(loginId = owner.id!!, importId = bookmarkImport.id!!)
                    snapshot.linkCount shouldBe bookmarkImport.linkCount
                    snapshot.folders.single().name shouldBe folderRow.name
                    snapshot.folders
                        .single()
                        .links
                        .single()
                        .title shouldBe linkRow.title
                    val state = preview.getReversalPreview(loginId = owner.id!!, importId = bookmarkImport.id!!).folders.single()
                    state.state shouldBe ReversalFolderState.DELETED
                    state.links.single().state shouldBe ReversalLinkState.DELETED
                    links.findById(importedLink.id!!) shouldBe null
                    folders.findById(source.id!!) shouldBe null
                }
            }
            When("cascade 뒤 reversal preview와 재취소를 순차 실행하면") {
                val deleteAndReverse =
                    suspend {
                        deletion.deleteFolder(loginId = owner.id!!, folderId = source.id!!, onDelete = "cascade")
                        preview.getReversalPreview(loginId = owner.id!!, importId = bookmarkImport.id!!)
                        repeat(2) {
                            reversal.createReversal(
                                loginId = owner.id!!,
                                importId = bookmarkImport.id!!,
                                request = CreateImportReversalRequest(linkIds = listOf(importedLink.id!!), folderIds = listOf(source.id!!)),
                            )
                        }
                    }
                Then("삭제된 link를 복구하거나 다시 취소 이력으로 저장하지 않는다") {
                    shouldNotThrow<BusinessException> { deleteAndReverse() }
                    verifySnapshots()
                    links.findById(importedLink.id!!) shouldBe null
                    folders.findById(source.id!!) shouldBe null
                    preview
                        .getReversalPreview(loginId = owner.id!!, importId = bookmarkImport.id!!)
                        .folders
                        .single()
                        .links
                        .single()
                        .state shouldBe ReversalLinkState.DELETED
                }
            }
            When("PENDING thumbnail 링크를 먼저 삭제하고 fill을 실행하면") {
                val deleteAndFill =
                    suspend {
                        deletion.deleteFolder(loginId = owner.id!!, folderId = source.id!!, onDelete = "cascade")
                        fill.fill(linkRow.id!!)
                    }
                Then("thumbnailStatus만 STOPPED가 되고 실체는 재생성되지 않는다") {
                    shouldNotThrow<BusinessException> { deleteAndFill() }
                    imports.findLinkById(linkRow.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    links.findById(importedLink.id!!) shouldBe null
                    client.calls.size shouldBe 0
                    verifySnapshots()
                }
            }
        }
    })
