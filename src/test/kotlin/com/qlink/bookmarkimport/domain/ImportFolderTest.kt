package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class ImportFolderTest :
    BehaviorSpec({
        Given("가져오기 폴더 행") {
            val ownerId = RandomFixture.randomId()
            val current = FolderFixture.createFolderWith(ownerId = ownerId)
            val row = ImportFixture.createImportFolderOf(importId = RandomFixture.randomId(), folderId = current.id!!)

            When("폴더가 지금 있으면") {
                val name = row.displayName(current)

                Then("지금 이름") {
                    name shouldBe current.name
                }
            }

            When("폴더가 지워졌으면") {
                val name = row.displayName(null)

                Then("가져올 때 이름") {
                    name shouldBe row.name
                }
            }

            When("되돌리기로 지웠으면") {
                val reversed = row.reverse(Clock.System.now())

                Then("REVERSED") {
                    reversed.reversalState(null) shouldBe ReversalFolderState.REVERSED
                }
            }

            When("사용자가 지웠으면") {
                val state = row.reversalState(null)

                Then("DELETED") {
                    state shouldBe ReversalFolderState.DELETED
                }
            }

            When("지금 공유 중이면") {
                val state = row.reversalState(FolderFixture.createSharedFolderOf(ownerId = ownerId))

                Then("SHARED") {
                    state shouldBe ReversalFolderState.SHARED
                }
            }

            When("그 밖이면") {
                val state = row.reversalState(current)

                Then("ACTIVE") {
                    state shouldBe ReversalFolderState.ACTIVE
                }
            }

            When("NEW이고 ACTIVE면") {
                val removable = row.isRemovable(current)

                Then("true") {
                    removable shouldBe true
                }
            }

            When("EXISTING이거나 SHARED·DELETED·REVERSED면") {
                val existing =
                    ImportFixture.createImportFolderOf(
                        importId = row.importId,
                        folderId = current.id!!,
                        kind = ImportFolderKind.EXISTING,
                    )
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId)
                val reversed = row.reverse(Clock.System.now())

                Then("false") {
                    existing.isRemovable(current) shouldBe false
                    row.isRemovable(shared) shouldBe false
                    row.isRemovable(null) shouldBe false
                    reversed.isRemovable(current) shouldBe false
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

                Then("주어진 값이 담기고 reversedAt은 null") {
                    row.importId shouldBe importId
                    row.folderId shouldBe folderId
                    row.kind shouldBe ImportFolderKind.NEW
                    row.name shouldBe name
                    row.reversedAt shouldBe null
                }
            }
        }
    })
