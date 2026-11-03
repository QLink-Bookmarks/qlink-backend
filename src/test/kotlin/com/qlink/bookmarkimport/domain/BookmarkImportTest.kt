@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrow
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
import kotlin.uuid.Uuid

class BookmarkImportTest :
    BehaviorSpec({
        Given("가져오기 기록을 만들 때") {
            val ownerId = RandomFixture.randomId()
            val clientRequestId = Uuid.random()

            fun create(
                linkCount: Int = RandomFixture.randomInt(1, 100),
                failedCount: Int = 0,
                fileName: String = ImportFixture.randomFileName(),
            ): BookmarkImport =
                BookmarkImport.create(
                    ownerId = ownerId,
                    clientRequestId = clientRequestId,
                    fileName = fileName,
                    linkCount = linkCount,
                    folderCount = RandomFixture.randomInt(0, 10),
                    skippedCount = RandomFixture.randomInt(0, 10),
                    failedCount = failedCount,
                )

            When("실패가 0이면") {
                val created = create(failedCount = 0)

                Then("COMPLETED") {
                    created.status shouldBe ImportStatus.COMPLETED
                    created.ownerId shouldBe ownerId
                    created.clientRequestId shouldBe clientRequestId
                    created.reversedAt shouldBe null
                }
            }

            When("만든 링크 0, 실패 0(전부 건너뜀)이면") {
                val created = create(linkCount = 0, failedCount = 0)

                Then("COMPLETED") {
                    created.status shouldBe ImportStatus.COMPLETED
                }
            }

            When("만든 링크 1개 이상, 실패 1개 이상이면") {
                val created = create(linkCount = RandomFixture.randomInt(1, 100), failedCount = RandomFixture.randomInt(1, 100))

                Then("PARTIALLY_FAILED") {
                    created.status shouldBe ImportStatus.PARTIALLY_FAILED
                }
            }

            When("만든 링크 0, 실패 1개 이상이면") {
                val created = create(linkCount = 0, failedCount = RandomFixture.randomInt(1, 100))

                Then("FAILED") {
                    created.status shouldBe ImportStatus.FAILED
                }
            }

            When("파일 이름이 공백뿐이면") {
                val action = { create(fileName = "  ") }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FILE_NAME_BLANK.message) {
                        action()
                    }
                }
            }

            When("파일 이름이 정확히 255자면") {
                val fileName = RandomFixture.randomFixedSentence(BookmarkImport.FILE_NAME_MAX_LENGTH)
                val created = create(fileName = fileName)

                Then("기록이 만들어진다") {
                    created.fileName shouldBe fileName
                }
            }

            When("파일 이름이 256자면") {
                val action = { create(fileName = RandomFixture.randomFixedSentence(BookmarkImport.FILE_NAME_MAX_LENGTH + 1)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FILE_NAME_OVER_MAX.message) {
                        action()
                    }
                }
            }
        }

        Given("다른 사용자의 가져오기 기록") {
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = RandomFixture.randomId())

            When("소유자를 검증하면") {
                val validate = { bookmarkImport.validateOwner(bookmarkImport.ownerId + 1) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_NOT_FOUND.message) {
                        validate()
                    }
                }
            }
        }

        Given("내 가져오기 기록") {
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = RandomFixture.randomId())

            When("소유자를 검증하면") {
                val validate = { bookmarkImport.validateOwner(bookmarkImport.ownerId) }

                Then("통과한다") {
                    shouldNotThrow<BusinessException> {
                        validate()
                    }
                }
            }
        }

        Given("만든 링크 수가 있는 기록") {
            val ownerId = RandomFixture.randomId()

            When("만든 링크가 0이면") {
                val progress = ImportFixture.createBookmarkImportOf(ownerId = ownerId, linkCount = 0).progress(0)

                Then("1") {
                    progress shouldBe 1.0
                }
            }

            When("완료·실패·멈춤 수가 일부면") {
                val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId, linkCount = 4)
                val progress = bookmarkImport.progress(1)

                Then("그 수 ÷ 만든 링크 수") {
                    progress shouldBe 0.25
                }
            }

            When("모두 끝났으면") {
                val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
                val progress = bookmarkImport.progress(bookmarkImport.linkCount)

                Then("1") {
                    progress shouldBe 1.0
                }
            }
        }

        Given("지울 수 있는 항목 수") {
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = RandomFixture.randomId())

            When("지울 수 있는 링크와 NEW 폴더가 모두 0이면") {
                val reversible = bookmarkImport.isReversible(removableLinkCount = 0, removableNewFolderCount = 0)

                Then("false") {
                    reversible shouldBe false
                }
            }

            When("둘 중 하나라도 1 이상이면") {
                val count = RandomFixture.randomInt(1, 100)

                Then("true") {
                    bookmarkImport.isReversible(removableLinkCount = count, removableNewFolderCount = 0) shouldBe true
                    bookmarkImport.isReversible(removableLinkCount = 0, removableNewFolderCount = count) shouldBe true
                }
            }
        }

        Given("되돌린 적 없는 기록") {
            val ownerId = RandomFixture.randomId()
            val savedStatuses = listOf(ImportStatus.COMPLETED, ImportStatus.PARTIALLY_FAILED, ImportStatus.FAILED)

            When("상태를 구하면") {
                val statuses =
                    savedStatuses.map { status ->
                        ImportFixture.createBookmarkImportOf(ownerId = ownerId, status = status).status(isReversible = true)
                    }

                Then("저장 결과 상태(COMPLETED/PARTIALLY_FAILED/FAILED)") {
                    statuses shouldContainExactly savedStatuses
                }
            }
        }

        Given("되돌린 적 있는 기록") {
            val bookmarkImport =
                ImportFixture.createBookmarkImportOf(
                    ownerId = RandomFixture.randomId(),
                    reversedAt = RandomFixture.randomPastInstant(),
                )

            When("지울 수 있는 항목이 남아 있으면") {
                val status = bookmarkImport.status(isReversible = true)

                Then("PARTIALLY_UNDONE") {
                    status shouldBe ImportStatus.PARTIALLY_UNDONE
                }
            }

            When("지울 수 있는 항목이 없으면") {
                val status = bookmarkImport.status(isReversible = false)

                Then("UNDONE") {
                    status shouldBe ImportStatus.UNDONE
                }
            }
        }

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
                bookmarkImport.reversalPreview(
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
        }

        Given("되돌리기 요청") {
            val ownerId = RandomFixture.randomId()
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
            val importId = bookmarkImport.id!!
            val now = Clock.System.now()
            val activeFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val sharedFolder = FolderFixture.createSharedFolderOf(ownerId = ownerId)
            val existingFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val deletedFolderId = RandomFixture.randomId()
            val activeRow = ImportFixture.createImportFolderOf(importId = importId, folderId = activeFolder.id!!)
            val sharedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = sharedFolder.id!!)
            val existingRow =
                ImportFixture.createImportFolderOf(
                    importId = importId,
                    folderId = existingFolder.id!!,
                    kind = ImportFolderKind.EXISTING,
                )
            val deletedRow = ImportFixture.createImportFolderOf(importId = importId, folderId = deletedFolderId)
            val reversedFolder = FolderFixture.createFolderWith(ownerId = ownerId)
            val reversedFolderRow =
                ImportFixture.createImportFolderOf(
                    importId = importId,
                    folderId = reversedFolder.id!!,
                    reversedAt = now,
                )
            val imported = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = activeFolder.id)
            val edited =
                LinkFixture.createRandomLinkOf(
                    ownerId = ownerId,
                    folderId = activeFolder.id,
                    memo = RandomFixture.randomSentenceWithMax(100),
                )
            val inShared = LinkFixture.createImportedLinkOf(ownerId = ownerId, folderId = sharedFolder.id)
            val addedLater = LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = activeFolder.id)
            val deletedLinkId = RandomFixture.randomId()
            val reversedLink = LinkFixture.createImportedLinkOf(ownerId = ownerId)
            val importedRow =
                ImportFixture.createImportLinkOf(
                    importId = importId,
                    linkId = imported.id!!,
                    importFolderId = activeRow.id,
                    url = imported.url,
                    title = imported.title,
                )
            val editedRow =
                ImportFixture.createImportLinkOf(
                    importId = importId,
                    linkId = edited.id!!,
                    importFolderId = activeRow.id,
                    url = edited.url,
                    title = edited.title,
                )
            val inSharedRow =
                ImportFixture.createImportLinkOf(
                    importId = importId,
                    linkId = inShared.id!!,
                    importFolderId = sharedRow.id,
                    url = inShared.url,
                    title = inShared.title,
                )
            val deletedRow2 = ImportFixture.createImportLinkOf(importId = importId, linkId = deletedLinkId, importFolderId = activeRow.id)
            val reversedRow =
                ImportFixture.createImportLinkOf(
                    importId = importId,
                    linkId = reversedLink.id!!,
                    importFolderId = null,
                    url = reversedLink.url,
                    title = reversedLink.title,
                    reversedAt = now,
                )

            fun reverse(
                linkIds: List<Long> = emptyList(),
                folderIds: List<Long> = emptyList(),
            ): ImportReversal =
                bookmarkImport.reverse(
                    linkIds = linkIds,
                    folderIds = folderIds,
                    folders = listOf(activeRow, sharedRow, existingRow, deletedRow, reversedFolderRow),
                    links = listOf(importedRow, editedRow, inSharedRow, deletedRow2, reversedRow),
                    currentLinks = listOf(imported, edited, inShared, addedLater, reversedLink),
                    currentFolders = listOf(activeFolder, sharedFolder, existingFolder, reversedFolder),
                    now = now,
                )

            When("linkIds·folderIds가 모두 비었으면") {
                val action = { reverse() }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEMS_EMPTY.message) {
                        action()
                    }
                }
            }

            When("EXISTING 폴더 id가 있으면") {
                val action = { reverse(folderIds = listOf(existingFolder.id!!)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID.message) {
                        action()
                    }
                }
            }

            When("ADDED_LATER 링크 id가 있으면") {
                val action = { reverse(linkIds = listOf(imported.id!!, addedLater.id!!)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID.message) {
                        action()
                    }
                }
            }

            When("이 가져오기와 무관한 id가 있으면") {
                val linkAction = { reverse(linkIds = listOf(RandomFixture.randomId())) }
                val folderAction = { reverse(folderIds = listOf(RandomFixture.randomId())) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID.message) {
                        linkAction()
                    }
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID.message) {
                        folderAction()
                    }
                }
            }

            When("고른 NEW 폴더가 지금 공유 중이면") {
                val action = { reverse(folderIds = listOf(sharedFolder.id!!)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED.message) {
                        action()
                    }
                }
            }

            When("IMPORTED·EDITED 링크와 ACTIVE NEW 폴더를 고르면") {
                val reversal = reverse(linkIds = listOf(imported.id!!, edited.id!!), folderIds = listOf(activeFolder.id!!))

                Then("지울 링크·폴더에 담기고, 항목 행은 reversedAt이 채워지며 기록 reversedAt이 채워진다") {
                    reversal.linkIds shouldContainExactly listOf(imported.id, edited.id)
                    reversal.folderIds shouldContainExactly listOf(activeFolder.id)
                    reversal.importLinks.map { it.id } shouldContainExactly listOf(importedRow.id, editedRow.id)
                    reversal.importLinks.forEach { it.reversedAt shouldBe now }
                    reversal.importFolders.map { it.id } shouldContainExactly listOf(activeRow.id)
                    reversal.importFolders.single().reversedAt shouldBe now
                    reversal.bookmarkImport.reversedAt shouldBe now
                }
            }

            When("링크만 고르면") {
                val reversal = reverse(linkIds = listOf(imported.id!!))

                Then("링크만 지울 대상이다") {
                    reversal.linkIds shouldContainExactly listOf(imported.id)
                    reversal.folderIds.shouldBeEmpty()
                    reversal.importFolders.shouldBeEmpty()
                }
            }

            When("폴더만 고르면") {
                val reversal = reverse(folderIds = listOf(activeFolder.id!!))

                Then("폴더만 지울 대상이다") {
                    reversal.folderIds shouldContainExactly listOf(activeFolder.id)
                    reversal.linkIds.shouldBeEmpty()
                    reversal.importLinks.shouldBeEmpty()
                }
            }

            When("이미 지워졌거나 되돌린 항목을 고르면") {
                val reversal =
                    reverse(
                        linkIds = listOf(deletedLinkId, reversedLink.id!!),
                        folderIds = listOf(deletedFolderId, reversedFolder.id!!),
                    )

                Then("거절하지 않고 지울 대상에서 뺀다") {
                    reversal.linkIds.shouldBeEmpty()
                    reversal.folderIds.shouldBeEmpty()
                    reversal.bookmarkImport.reversedAt shouldBe bookmarkImport.reversedAt
                }
            }

            When("공유된 NEW 폴더는 고르지 않고 그 안의 가져온 링크만 고르면") {
                val reversal = reverse(linkIds = listOf(inShared.id!!))

                Then("거절하지 않고 그 링크들만 지울 대상이다") {
                    reversal.linkIds shouldContainExactly listOf(inShared.id)
                    reversal.folderIds.shouldBeEmpty()
                }
            }
        }
    })
