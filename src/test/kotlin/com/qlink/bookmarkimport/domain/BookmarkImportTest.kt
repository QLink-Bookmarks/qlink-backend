@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Instant
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

        Given("항목 행과 되돌리기 상태") {
            val ownerId = RandomFixture.randomId()
            val bookmarkImport = ImportFixture.createBookmarkImportOf(ownerId = ownerId)
            val importId = bookmarkImport.id!!
            val now = Clock.System.now()

            fun folderRow(
                kind: ImportFolderKind = ImportFolderKind.NEW,
                reversedAt: Instant? = null,
            ): ImportFolder =
                ImportFixture.createImportFolderOf(
                    importId = importId,
                    folderId = RandomFixture.randomId(),
                    kind = kind,
                    reversedAt = reversedAt,
                )

            fun linkRow(reversedAt: Instant? = null): ImportLink =
                ImportFixture.createImportLinkOf(importId = importId, linkId = RandomFixture.randomId(), reversedAt = reversedAt)

            val active = ImportFixture.createFolderStateOf(folderRow())
            val shared = ImportFixture.createFolderStateOf(folderRow(), isShared = true)
            val existing = ImportFixture.createFolderStateOf(folderRow(ImportFolderKind.EXISTING))
            val deletedFolder = ImportFixture.createFolderStateOf(folderRow(), isDeleted = true)
            val reversedFolder = ImportFixture.createFolderStateOf(folderRow(reversedAt = now), isDeleted = true)
            val imported = ImportFixture.createLinkStateOf(linkRow())
            val edited = ImportFixture.createLinkStateOf(linkRow(), isEdited = true)
            val inShared = ImportFixture.createLinkStateOf(linkRow())
            val deletedLink = ImportFixture.createLinkStateOf(linkRow(), isDeleted = true)
            val reversedLink = ImportFixture.createLinkStateOf(linkRow(reversedAt = now), isDeleted = true)
            val addedLaterLinkId = RandomFixture.randomId()

            fun reverse(
                linkIds: List<Long> = emptyList(),
                folderIds: List<Long> = emptyList(),
            ): ImportReversal =
                bookmarkImport.reverse(
                    linkIds = linkIds,
                    folderIds = folderIds,
                    folders = listOf(active, shared, existing, deletedFolder, reversedFolder),
                    links = listOf(imported, edited, inShared, deletedLink, reversedLink),
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
                val action = { reverse(folderIds = listOf(existing.folder.folderId)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID.message) {
                        action()
                    }
                }
            }

            When("가져오기 링크 행이 아닌 링크 id(ADDED_LATER)가 있으면") {
                val action = { reverse(linkIds = listOf(imported.link.linkId, addedLaterLinkId)) }

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

            When("고른 NEW 폴더의 상태가 SHARED면") {
                val action = { reverse(folderIds = listOf(shared.folder.folderId)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED.message) {
                        action()
                    }
                }
            }

            When("IMPORTED·EDITED 링크와 ACTIVE NEW 폴더를 고르면") {
                val reversal =
                    reverse(
                        linkIds = listOf(imported.link.linkId, edited.link.linkId),
                        folderIds = listOf(active.folder.folderId),
                    )

                Then("지울 링크·폴더에 담기고, 항목 행은 reversedAt이 채워지며 기록 reversedAt이 채워진다") {
                    reversal.linkIds shouldContainExactly listOf(imported.link.linkId, edited.link.linkId)
                    reversal.folderIds shouldContainExactly listOf(active.folder.folderId)
                    reversal.importLinks.map { it.id } shouldContainExactly listOf(imported.link.id, edited.link.id)
                    reversal.importLinks.forEach { it.reversedAt shouldBe now }
                    reversal.importFolders.map { it.id } shouldContainExactly listOf(active.folder.id)
                    reversal.importFolders.single().reversedAt shouldBe now
                    reversal.bookmarkImport.reversedAt shouldBe now
                }
            }

            When("링크만 고르면") {
                val reversal = reverse(linkIds = listOf(imported.link.linkId))

                Then("링크만 지울 대상이다") {
                    reversal.linkIds shouldContainExactly listOf(imported.link.linkId)
                    reversal.folderIds.shouldBeEmpty()
                    reversal.importFolders.shouldBeEmpty()
                }
            }

            When("폴더만 고르면") {
                val reversal = reverse(folderIds = listOf(active.folder.folderId))

                Then("폴더만 지울 대상이다") {
                    reversal.folderIds shouldContainExactly listOf(active.folder.folderId)
                    reversal.linkIds.shouldBeEmpty()
                    reversal.importLinks.shouldBeEmpty()
                }
            }

            When("DELETED·REVERSED 항목을 고르면") {
                val reversal =
                    reverse(
                        linkIds = listOf(deletedLink.link.linkId, reversedLink.link.linkId),
                        folderIds = listOf(deletedFolder.folder.folderId, reversedFolder.folder.folderId),
                    )

                Then("거절하지 않고 지울 대상에서 뺀다") {
                    reversal.linkIds.shouldBeEmpty()
                    reversal.folderIds.shouldBeEmpty()
                    reversal.bookmarkImport.reversedAt shouldBe bookmarkImport.reversedAt
                }
            }

            When("SHARED NEW 폴더는 고르지 않고 그 안의 가져온 링크만 고르면") {
                val reversal = reverse(linkIds = listOf(inShared.link.linkId))

                Then("거절하지 않고 그 링크들만 지울 대상이다") {
                    reversal.linkIds shouldContainExactly listOf(inShared.link.linkId)
                    reversal.folderIds.shouldBeEmpty()
                }
            }
        }
    })
