@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.ImportReversal
import com.qlink.bookmarkimport.dto.ReversalPreviewFolder
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

class ImportTrackDomainServiceTest :
    BehaviorSpec({
        Given("가져오기 항목 행과 지금의 링크·폴더") {
            val ownerId = RandomFixture.randomId()
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
            val importId = bookmarkImport.id!!
            val firstFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val secondFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val unrelatedFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val firstRow = ImportFixture.createImportFolderOf(importId = importId, folderId = firstFolder.id!!)
            val secondRow = ImportFixture.createImportFolderOf(importId = importId, folderId = secondFolder.id!!)
            val stayed = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = firstFolder.id)
            val renamed = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = firstFolder.id)
            val movedToImported = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = secondFolder.id)
            val movedToUnrelated = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = unrelatedFolder.id)
            val unfiled = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = null)
            val deletedLinkId = RandomFixture.randomId()
            val addedLater = LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = firstFolder.id)
            val renamedTitle = "${RandomFixture.randomSentenceWithMax(200)} ${RandomFixture.randomId()}"
            val rows =
                listOf(
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = stayed.id!!,
                        importFolderId = firstRow.id,
                        url = stayed.url,
                        title = stayed.title,
                    ),
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = renamed.id!!,
                        importFolderId = firstRow.id,
                        url = renamed.url,
                        title = renamed.title,
                    ),
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = movedToImported.id!!,
                        importFolderId = firstRow.id,
                        url = movedToImported.url,
                        title = movedToImported.title,
                    ),
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = movedToUnrelated.id!!,
                        importFolderId = firstRow.id,
                        url = movedToUnrelated.url,
                        title = movedToUnrelated.title,
                    ),
                    ImportFixture.createImportLinkOf(importId = importId, linkId = deletedLinkId, importFolderId = secondRow.id),
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = unfiled.id!!,
                        importFolderId = null,
                        url = unfiled.url,
                        title = unfiled.title,
                    ),
                )
            val currentLinks =
                listOf(
                    stayed,
                    LinkFixture.createRandomLinkOf(
                        ownerId = ownerId,
                        id = renamed.id!!,
                        folderId = firstFolder.id,
                        url = renamed.url,
                        title = renamedTitle,
                        tags = emptyList(),
                    ),
                    movedToImported,
                    movedToUnrelated,
                    unfiled,
                    addedLater,
                )
            val preview =
                ImportTrackDomainService.reversalPreview(
                    folders = listOf(firstRow, secondRow),
                    links = rows,
                    currentLinks = currentLinks,
                    currentFolders = listOf(firstFolder, secondFolder, unrelatedFolder),
                )

            fun groupOf(folderId: Long?): ReversalPreviewFolder = preview.folders.single { it.folderId == folderId }

            When("가져온 링크가 지금 그 폴더에 있으면") {
                val group = groupOf(firstFolder.id)

                Then("지금 폴더 묶음 아래 지금 제목으로 놓인다") {
                    group.name shouldBe firstFolder.name
                    group.links.single { it.linkId == stayed.id }.state shouldBe ReversalLinkState.IMPORTED
                    group.links.single { it.linkId == renamed.id }.title shouldBe renamedTitle
                    group.links.single { it.linkId == renamed.id }.state shouldBe ReversalLinkState.EDITED
                }
            }

            When("가져온 링크를 다른 가져오기 폴더로 옮겼으면") {
                val group = groupOf(secondFolder.id)

                Then("옮긴 폴더 묶음 아래 놓인다") {
                    group.links.map { it.linkId } shouldContainExactlyInAnyOrder listOf(movedToImported.id, deletedLinkId)
                    groupOf(firstFolder.id).links.map { it.linkId } shouldNotContain movedToImported.id
                }
            }

            When("가져온 링크를 가져오기와 무관한 폴더로 옮겼으면") {
                val group = groupOf(unrelatedFolder.id)

                Then("그 폴더 묶음이 kind=EXISTING, state=ACTIVE로 생긴다") {
                    group.kind shouldBe ImportFolderKind.EXISTING
                    group.state shouldBe ReversalFolderState.ACTIVE
                    group.name shouldBe unrelatedFolder.name
                    group.links.map { it.linkId } shouldContainExactly listOf(movedToUnrelated.id)
                }
            }

            When("가져온 링크가 지워졌으면") {
                val row = rows.single { it.linkId == deletedLinkId }
                val link = groupOf(secondFolder.id).links.single { it.linkId == deletedLinkId }

                Then("가져올 때 폴더 묶음 아래 가져올 때 제목으로 놓인다") {
                    link.title shouldBe row.title
                    link.url shouldBe row.url
                    link.state shouldBe ReversalLinkState.DELETED
                }
            }

            When("NEW 폴더에 가져오기 밖의 링크가 있으면") {
                val link = groupOf(firstFolder.id).links.single { it.linkId == addedLater.id }

                Then("그 링크는 ADDED_LATER") {
                    link.state shouldBe ReversalLinkState.ADDED_LATER
                }
            }

            When("링크가 미분류에 있으면") {
                val group = preview.folders.single { it.kind == ImportFolderKind.UNFILED }

                Then("UNFILED 묶음(folderId null)에 놓이고, 다른 묶음은 모두 folderId가 있다") {
                    group.folderId shouldBe null
                    group.links.map { it.linkId } shouldContainExactly listOf(unfiled.id)
                    preview.folders.filter { it.kind != ImportFolderKind.UNFILED }.forEach { it.folderId shouldNotBe null }
                    preview.folders shouldHaveSize 4
                }
            }

            When("지금 폴더가 공유 중이고 가져온 링크를 고쳤으면") {
                val sharedFolder = FolderFixture.createSharedFolderOf(ownerId = ownerId)
                val sharedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = sharedFolder.id!!)
                val deletedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = RandomFixture.randomId())
                val importedLink = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = sharedFolder.id)
                val row =
                    ImportFixture.createImportLinkOf(
                        importId = importId,
                        linkId = importedLink.id!!,
                        importFolderId = sharedRow.id,
                        url = importedLink.url,
                        title = importedLink.title,
                    )
                val editedLink =
                    LinkFixture.createRandomLinkOf(
                        ownerId = ownerId,
                        id = importedLink.id,
                        folderId = sharedFolder.id,
                        url = importedLink.url,
                        title = importedLink.title,
                        memo = RandomFixture.randomSentenceWithMax(100),
                    )
                val group =
                    ImportTrackDomainService
                        .reversalPreview(
                            folders = listOf(sharedRow),
                            links = listOf(row),
                            currentLinks = listOf(editedLink),
                            currentFolders = listOf(sharedFolder),
                        ).folders
                        .single()

                Then("폴더는 SHARED, 링크는 EDITED다") {
                    group.state shouldBe ReversalFolderState.SHARED
                    group.links.single().state shouldBe ReversalLinkState.EDITED
                }
            }
        }

        Given("지금의 링크·폴더와 되돌리기 요청") {
            val ownerId = RandomFixture.randomId()
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
            val importId = bookmarkImport.id!!
            val now = Clock.System.now()
            val existing = LinkFixture.createImportedLinkOf(ownerId = ownerId)
            val existingRow =
                ImportFixture.createImportLinkOf(importId = importId, linkId = existing.id!!, url = existing.url, title = existing.title)
            val deletedRow = ImportFixture.createImportLinkOf(importId = importId, linkId = RandomFixture.randomId())

            When("지워진 링크와 지금 있는 링크를 고르면") {
                val reversal =
                    ImportTrackDomainService.reverse(
                        bookmarkImport = bookmarkImport,
                        linkIds = listOf(existingRow.linkId, deletedRow.linkId),
                        folderIds = emptyList(),
                        folders = emptyList(),
                        links = listOf(existingRow, deletedRow),
                        currentLinks = listOf(existing),
                        currentFolders = emptyList(),
                        now = now,
                    )

                Then("지금 있는 링크만 지울 대상이고, 항목 행과 기록에 reversedAt이 채워진다") {
                    reversal.linkIds shouldContainExactly listOf(existing.id)
                    reversal.importLinks.map { it.id } shouldContainExactly listOf(existingRow.id)
                    reversal.importLinks.single().reversedAt shouldBe now
                    reversal.bookmarkImport.reversedAt shouldBe now
                }
            }
        }

        Given("지금의 폴더와 NEW 폴더 되돌리기 요청") {
            val ownerId = RandomFixture.randomId()
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
            val importId = bookmarkImport.id!!
            val now = Clock.System.now()
            val activeFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val sharedFolder = FolderFixture.createSharedFolderOf(ownerId = ownerId)
            val activeRow = ImportFixture.createImportFolderOf(importId = importId, folderId = activeFolder.id!!)
            val sharedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = sharedFolder.id!!)
            val deletedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = RandomFixture.randomId())

            fun reverse(folderIds: List<Long>): ImportReversal =
                ImportTrackDomainService.reverse(
                    bookmarkImport = bookmarkImport,
                    linkIds = emptyList(),
                    folderIds = folderIds,
                    folders = listOf(activeRow, sharedRow, deletedRow),
                    links = emptyList(),
                    currentLinks = emptyList(),
                    currentFolders = listOf(activeFolder, sharedFolder),
                    now = now,
                )

            When("지금 공유 중인 NEW 폴더를 고르면") {
                val action = { reverse(folderIds = listOf(sharedFolder.id!!)) }

                Then("그 폴더 id가 sharedFolderIds로 넘겨져 되돌리기가 거절된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED.message) {
                        action()
                    }
                }
            }

            When("지금 공유 아닌 NEW 폴더를 고르면") {
                val reversal = reverse(folderIds = listOf(activeFolder.id!!))

                Then("지울 폴더에 담기고, 그 행에 reversedAt이 채워진다") {
                    reversal.folderIds shouldContainExactly listOf(activeFolder.id)
                    reversal.importFolders.map { it.id } shouldContainExactly listOf(activeRow.id)
                    reversal.importFolders.single().reversedAt shouldBe now
                }
            }

            When("지금은 없는 NEW 폴더를 고르면") {
                val reversal = reverse(folderIds = listOf(deletedRow.folderId))

                Then("DELETED라 거절하지 않고 지울 대상에서 빼며, 기록은 그대로다") {
                    reversal.folderIds.shouldBeEmpty()
                    reversal.importFolders.shouldBeEmpty()
                    reversal.bookmarkImport.reversedAt shouldBe bookmarkImport.reversedAt
                }
            }
        }

        Given("가져오기 폴더 행과 지금의 폴더") {
            val ownerId = RandomFixture.randomId()
            val current = FolderFixture.createFolderWith(ownerId = ownerId)
            val row = ImportFixture.createImportFolderOf(importId = RandomFixture.randomId(), folderId = current.id!!)

            When("폴더가 지금 있으면") {
                val name = ImportTrackDomainService.folderName(row, current)

                Then("지금 이름") {
                    name shouldBe current.name
                }
            }

            When("폴더가 지워졌으면") {
                val name = ImportTrackDomainService.folderName(row, null)

                Then("가져올 때 이름") {
                    name shouldBe row.name
                }
            }
        }

        Given("썸네일 작업 행과 지금의 링크") {
            val ownerId = RandomFixture.randomId()
            val row = ImportFixture.createImportLinkOf(importId = RandomFixture.randomId(), linkId = RandomFixture.randomId())

            When("링크가 지워졌으면") {
                val prepared = ImportTrackDomainService.prepareThumbnail(row, null)

                Then("STOPPED") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                }
            }

            When("링크에 파비콘만 있으면") {
                val current = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = null, faviconUrl = RandomFixture.randomUrl())
                val prepared = ImportTrackDomainService.prepareThumbnail(row, current)

                Then("DONE") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.DONE
                }
            }
        }
    })
