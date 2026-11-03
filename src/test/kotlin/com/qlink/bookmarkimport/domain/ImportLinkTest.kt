package com.qlink.bookmarkimport.domain

import com.qlink.link.domain.Link
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
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

        Given("가져오기 링크 행과 지금의 링크") {
            val ownerId = RandomFixture.randomId()
            val imported = LinkFixture.createImportedLinkOf(ownerId = ownerId)
            val row =
                ImportFixture.createImportLinkOf(
                    importId = RandomFixture.randomId(),
                    linkId = RandomFixture.randomId(),
                    url = imported.url,
                    title = imported.title,
                )

            fun Link.edited(
                url: String = this.url,
                title: String = this.title,
                memo: String? = this.memo,
                tags: List<String> = this.tags,
                folderId: Long? = this.folderId,
            ): Link =
                LinkFixture.createRandomLinkOf(
                    ownerId = ownerId,
                    folderId = folderId,
                    url = url,
                    title = title,
                    memo = memo,
                    tags = tags,
                    thumbnailUrl = thumbnailUrl,
                )

            When("되돌리기로 지웠으면") {
                val state = row.reverse(Clock.System.now()).reversalState(null)

                Then("REVERSED") {
                    state shouldBe ReversalLinkState.REVERSED
                }
            }

            When("사용자가 지웠으면") {
                val state = row.reversalState(null)

                Then("DELETED") {
                    state shouldBe ReversalLinkState.DELETED
                }
            }

            When("url이나 title이 가져올 때와 다르면") {
                val urlChanged = row.reversalState(imported.edited(url = RandomFixture.randomUrl()))
                val titleChanged = row.reversalState(imported.edited(title = "${imported.title.take(200)} ${RandomFixture.randomId()}"))

                Then("EDITED") {
                    urlChanged shouldBe ReversalLinkState.EDITED
                    titleChanged shouldBe ReversalLinkState.EDITED
                }
            }

            When("memo나 tags가 비어 있지 않으면") {
                val memoAdded = row.reversalState(imported.edited(memo = RandomFixture.randomSentenceWithMax(100)))
                val tagsAdded = row.reversalState(imported.edited(tags = listOf("tag-${RandomFixture.randomId()}")))

                Then("EDITED") {
                    memoAdded shouldBe ReversalLinkState.EDITED
                    tagsAdded shouldBe ReversalLinkState.EDITED
                }
            }

            When("즐겨찾기·폴더 이동·썸네일·AI 요약만 바뀌었으면") {
                val current =
                    LinkFixture.createRandomLinkOf(
                        ownerId = ownerId,
                        folderId = RandomFixture.randomId(),
                        url = imported.url,
                        title = imported.title,
                        summary = RandomFixture.randomSentenceWithMax(100),
                        tags = emptyList(),
                        thumbnailUrl = RandomFixture.randomUrl(),
                        faviconUrl = RandomFixture.randomUrl(),
                        favoriteAt = RandomFixture.randomPastInstant(),
                    )
                val state = row.reversalState(current)

                Then("IMPORTED") {
                    state shouldBe ReversalLinkState.IMPORTED
                }
            }

            When("IMPORTED·EDITED면") {
                val importedRemovable = row.isRemovable(imported)
                val editedRemovable = row.isRemovable(imported.edited(memo = RandomFixture.randomSentenceWithMax(100)))

                Then("true") {
                    importedRemovable shouldBe true
                    editedRemovable shouldBe true
                }
            }

            When("DELETED·REVERSED면") {
                val deletedRemovable = row.isRemovable(null)
                val reversedRemovable = row.reverse(Clock.System.now()).isRemovable(imported)

                Then("false") {
                    deletedRemovable shouldBe false
                    reversedRemovable shouldBe false
                }
            }
        }

        Given("썸네일 작업 상태별 행") {
            val ownerId = RandomFixture.randomId()

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
                val prepared = rowOf(ThumbnailStatus.PENDING).prepareThumbnail(null)

                Then("STOPPED, 호출하지 않음") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    prepared.isThumbnailPending shouldBe false
                }
            }

            When("링크에 썸네일이 있으면") {
                val current = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = RandomFixture.randomUrl(), faviconUrl = null)
                val prepared = rowOf(ThumbnailStatus.PENDING).prepareThumbnail(current)

                Then("DONE, 호출하지 않음") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    prepared.isThumbnailPending shouldBe false
                }
            }

            When("링크에 파비콘만 있으면") {
                val current = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = null, faviconUrl = RandomFixture.randomUrl())
                val prepared = rowOf(ThumbnailStatus.PENDING).prepareThumbnail(current)

                Then("DONE, 호출하지 않음") {
                    prepared.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    prepared.isThumbnailPending shouldBe false
                }
            }

            When("썸네일·파비콘이 둘 다 비었으면") {
                val row = rowOf(ThumbnailStatus.PENDING)
                val prepared = row.prepareThumbnail(LinkFixture.createImportedLinkOf(ownerId = ownerId))

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
