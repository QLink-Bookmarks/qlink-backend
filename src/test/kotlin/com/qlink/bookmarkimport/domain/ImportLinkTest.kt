package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class ImportLinkTest :
    BehaviorSpec({
        Given("가져오기 링크 행을 만들 때") {
            val importId = RandomFixture.randomId()
            val linkId = RandomFixture.randomId()
            val importFolderId = RandomFixture.randomId()
            val url = RandomFixture.randomUrl()
            val title = RandomFixture.randomSentenceWithMax(300)

            When("만들면") {
                val row = ImportLink.create(importId = importId, linkId = linkId, importFolderId = importFolderId, url = url, title = title)

                Then("썸네일 작업은 PENDING, startedAt·reversedAt은 null") {
                    row.thumbnailStatus shouldBe ThumbnailStatus.PENDING
                    row.startedAt shouldBe null
                    row.reversedAt shouldBe null
                    row.importId shouldBe importId
                    row.linkId shouldBe linkId
                    row.importFolderId shouldBe importFolderId
                    row.url shouldBe url
                    row.title shouldBe title
                }
            }
        }

        Given("가져오기 링크 행") {
            val row = ImportFixture.createImportLinkOf(importId = RandomFixture.randomId(), linkId = RandomFixture.randomId())

            When("되돌리기로 지웠으면") {
                val state = row.reverse(Clock.System.now()).reversalState(isDeleted = true, isEdited = true)

                Then("REVERSED") {
                    state shouldBe ReversalLinkState.REVERSED
                }
            }

            When("지워졌으면") {
                val state = row.reversalState(isDeleted = true, isEdited = false)

                Then("DELETED") {
                    state shouldBe ReversalLinkState.DELETED
                }
            }

            When("수정됐으면") {
                val state = row.reversalState(isDeleted = false, isEdited = true)

                Then("EDITED") {
                    state shouldBe ReversalLinkState.EDITED
                }
            }

            When("그 밖이면") {
                val state = row.reversalState(isDeleted = false, isEdited = false)

                Then("IMPORTED") {
                    state shouldBe ReversalLinkState.IMPORTED
                }
            }

            When("되돌린 행에 지금 상태를 붙이면") {
                val state = row.reverse(Clock.System.now()).withState(isDeleted = false, isEdited = false)

                Then("행에서 정한 REVERSED가 붙는다") {
                    state.state shouldBe ReversalLinkState.REVERSED
                }
            }

            When("수정된 행에 상태를 붙이면") {
                val state = row.withState(isDeleted = false, isEdited = true)

                Then("그 행과 EDITED가 함께 담긴다") {
                    state.link shouldBe row
                    state.state shouldBe ReversalLinkState.EDITED
                }
            }

            When("상태가 IMPORTED·EDITED면") {
                val importedRemovable = row.isRemovable(ReversalLinkState.IMPORTED)
                val editedRemovable = row.isRemovable(ReversalLinkState.EDITED)

                Then("true") {
                    importedRemovable shouldBe true
                    editedRemovable shouldBe true
                }
            }

            When("상태가 DELETED·REVERSED면") {
                val deletedRemovable = row.isRemovable(ReversalLinkState.DELETED)
                val reversedRemovable = row.isRemovable(ReversalLinkState.REVERSED)

                Then("false") {
                    deletedRemovable shouldBe false
                    reversedRemovable shouldBe false
                }
            }
        }

        Given("썸네일 작업 상태별 행") {
            fun rowOf(status: ThumbnailStatus): ImportLink =
                ImportFixture.createImportLinkOf(
                    importId = RandomFixture.randomId(),
                    linkId = RandomFixture.randomId(),
                    thumbnailStatus = status,
                    startedAt = RandomFixture.randomPastInstant(),
                )

            When("PENDING 행을 되돌리면") {
                val now = Clock.System.now()
                val reversed = rowOf(ThumbnailStatus.PENDING).reverse(now)

                Then("STOPPED, reversedAt이 채워진다") {
                    reversed.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    reversed.reversedAt shouldBe now
                }
            }

            When("DONE·FAILED 행을 되돌리면") {
                val now = Clock.System.now()
                val done = rowOf(ThumbnailStatus.DONE).reverse(now)
                val failed = rowOf(ThumbnailStatus.FAILED).reverse(now)

                Then("작업 상태는 그대로, reversedAt만 채워진다") {
                    done.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    failed.thumbnailStatus shouldBe ThumbnailStatus.FAILED
                    done.reversedAt shouldBe now
                    failed.reversedAt shouldBe now
                }
            }

            When("링크가 지워졌으면") {
                val prepared = rowOf(ThumbnailStatus.PENDING).prepareThumbnail(isDeleted = true, hasPreview = false)

                Then("STOPPED, 호출하지 않음") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    prepared.isThumbnailPending shouldBe false
                }
            }

            When("링크에 미리보기가 있으면") {
                val prepared = rowOf(ThumbnailStatus.PENDING).prepareThumbnail(isDeleted = false, hasPreview = true)

                Then("DONE, 호출하지 않음") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    prepared.isThumbnailPending shouldBe false
                }
            }

            When("미리보기가 없으면") {
                val row = rowOf(ThumbnailStatus.PENDING)
                val prepared = row.prepareThumbnail(isDeleted = false, hasPreview = false)

                Then("실행한다(상태 그대로)") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.PENDING
                    prepared.isThumbnailPending shouldBe true
                    prepared.startedAt shouldBe row.startedAt
                }
            }

            When("완료하면") {
                val completed = rowOf(ThumbnailStatus.PENDING).completeThumbnail()

                Then("DONE") {
                    completed.thumbnailStatus shouldBe ThumbnailStatus.DONE
                }
            }

            When("실패하면") {
                val failed = rowOf(ThumbnailStatus.PENDING).failThumbnail()

                Then("FAILED") {
                    failed.thumbnailStatus shouldBe ThumbnailStatus.FAILED
                }
            }

            When("스로틀링으로 되돌리면") {
                val released = rowOf(ThumbnailStatus.PENDING).releaseThumbnail()

                Then("PENDING, startedAt은 null") {
                    released.thumbnailStatus shouldBe ThumbnailStatus.PENDING
                    released.startedAt shouldBe null
                }
            }
        }
    })
