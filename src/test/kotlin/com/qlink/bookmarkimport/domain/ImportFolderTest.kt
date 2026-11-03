package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class ImportFolderTest :
    BehaviorSpec({
        Given("가져오기 폴더 행") {
            val row = ImportFixture.createImportFolderOf(importId = RandomFixture.randomId(), folderId = RandomFixture.randomId())

            When("지금 이름이 있으면") {
                val currentName = RandomFixture.randomSentenceWithMax(100)
                val name = row.displayName(currentName)

                Then("지금 이름") {
                    name shouldBe currentName
                }
            }

            When("지금 이름이 없으면(지워짐)") {
                val name = row.displayName(null)

                Then("가져올 때 이름") {
                    name shouldBe row.name
                }
            }

            When("되돌리기로 지웠으면") {
                val reversed = row.reverse(Clock.System.now())
                val state = reversed.reversalState(ImportFixture.createReversalFactsOf(deletedFolderIds = setOf(row.folderId)))

                Then("REVERSED") {
                    state shouldBe ReversalFolderState.REVERSED
                }
            }

            When("지워졌으면") {
                val state = row.reversalState(ImportFixture.createReversalFactsOf(deletedFolderIds = setOf(row.folderId)))

                Then("DELETED") {
                    state shouldBe ReversalFolderState.DELETED
                }
            }

            When("공유 중이면") {
                val state = row.reversalState(ImportFixture.createReversalFactsOf(sharedFolderIds = setOf(row.folderId)))

                Then("SHARED") {
                    state shouldBe ReversalFolderState.SHARED
                }
            }

            When("그 밖이면") {
                val state = row.reversalState(ImportFixture.createReversalFactsOf())

                Then("ACTIVE") {
                    state shouldBe ReversalFolderState.ACTIVE
                }
            }

            When("NEW이고 상태가 ACTIVE면") {
                val removable = row.isRemovable(ReversalFolderState.ACTIVE)

                Then("true") {
                    removable shouldBe true
                }
            }

            When("EXISTING이거나 상태가 SHARED·DELETED·REVERSED면") {
                val existing =
                    ImportFixture.createImportFolderOf(
                        importId = row.importId,
                        folderId = row.folderId,
                        kind = ImportFolderKind.EXISTING,
                    )
                val inactiveStates = listOf(ReversalFolderState.SHARED, ReversalFolderState.DELETED, ReversalFolderState.REVERSED)

                Then("false") {
                    existing.isRemovable(ReversalFolderState.ACTIVE) shouldBe false
                    inactiveStates.forEach { row.isRemovable(it) shouldBe false }
                }
            }

            When("되돌리면") {
                val now = Clock.System.now()
                val reversed = row.reverse(now)

                Then("reversedAt이 주어진 시각이다") {
                    reversed.reversedAt shouldBe now
                    reversed.id shouldBe row.id
                    reversed.folderId shouldBe row.folderId
                    reversed.kind shouldBe row.kind
                    reversed.name shouldBe row.name
                }
            }
        }

        Given("가져오기 폴더 행을 만들 때") {
            val importId = RandomFixture.randomId()
            val folderId = RandomFixture.randomId()
            val name = RandomFixture.randomSentenceWithMax(100)

            When("만들면") {
                val row = ImportFolder.create(importId = importId, folderId = folderId, kind = ImportFolderKind.NEW, name = name)

                Then("주어진 가져오기·폴더·종류·이름이 담기고 reversedAt은 null") {
                    row.importId shouldBe importId
                    row.folderId shouldBe folderId
                    row.kind shouldBe ImportFolderKind.NEW
                    row.name shouldBe name
                    row.reversedAt shouldBe null
                }
            }
        }
    })
