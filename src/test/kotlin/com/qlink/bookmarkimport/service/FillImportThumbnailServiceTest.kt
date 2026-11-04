@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.link.client.LinkPreviewCaller
import com.qlink.link.client.LinkPreviewExtraction
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.dbNow
import com.qlink.support.fixture.FakeLinkPreviewSyncClient
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.LinkPreviewFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

private const val PUBLIC_BASE_URL = "https://images.archivelink.app"

class FillImportThumbnailServiceTest :
    BaseServiceTest({
        val service = koinGet<FillImportThumbnailService>()
        val client = koinGet<FakeLinkPreviewSyncClient>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()

        suspend fun insertImportRowOf(
            link: Link,
            thumbnailStatus: ThumbnailStatus = ThumbnailStatus.PENDING,
            startedAt: Instant? = null,
            reversedAt: Instant? = null,
        ): ImportLink {
            val bookmarkImport = bookmarkImportRepository.insert(ImportFixture.createBookmarkImportOf(ownerId = link.ownerId))
            val inserted =
                bookmarkImportRepository.insertLinks(
                    listOf(
                        ImportFixture.createImportLinkOf(
                            importId = bookmarkImport.id!!,
                            linkId = link.id!!,
                            url = link.url,
                            title = link.title,
                            thumbnailStatus = thumbnailStatus,
                            startedAt = startedAt,
                            reversedAt = reversedAt,
                        ),
                    ),
                )
            return bookmarkImportRepository.findLinkById(inserted.single().id!!)!!
        }

        suspend fun insertImportedLink(): Link {
            val user = userRepository.insert(UserFixture.createRandomValidUser())
            return linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!))
        }

        Given("썸네일·파비콘이 빈 링크의 PENDING 가져오기 링크 행") {
            lateinit var link: Link
            lateinit var row: ImportLink

            beforeEach {
                client.reset()
                link = insertImportedLink()
                row = insertImportRowOf(link = link)
            }

            When("Lambda가 썸네일을 돌려주면") {
                val thumbnailKey = LinkPreviewFixture.key(kind = "preview")
                val isVideo = RandomFixture.randomInt(0, 1) == 1
                val fill =
                    suspend {
                        client.respond(
                            LinkPreviewResult(
                                title = RandomFixture.randomSentenceWithMax(300),
                                thumbnailKey = thumbnailKey,
                                isVideo = isVideo,
                            ),
                        )
                        service.fill(row.id!!)
                    }

                Then("링크 thumbnail_url·is_video가 채워지고 제목은 그대로, 행은 DONE, started_at이 채워진다") {
                    fill()

                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe "$PUBLIC_BASE_URL/$thumbnailKey"
                    actualLink.isVideo shouldBe isVideo
                    actualLink.title shouldBe link.title
                    val actualRow = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actualRow.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    actualRow.startedAt.shouldNotBeNull()
                    client.callers.single() shouldBe LinkPreviewCaller.IMPORT_THUMBNAIL
                }
            }

            When("Lambda가 썸네일과 파비콘을 함께 돌려주면") {
                val thumbnailKey = LinkPreviewFixture.key(kind = "preview")
                val fill =
                    suspend {
                        client.respond(
                            LinkPreviewResult(thumbnailKey = thumbnailKey, faviconKey = LinkPreviewFixture.key(kind = "favicon")),
                        )
                        service.fill(row.id!!)
                    }

                Then("썸네일만 채워지고 favicon_url은 비어 있다") {
                    fill()

                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe "$PUBLIC_BASE_URL/$thumbnailKey"
                    actualLink.faviconUrl shouldBe null
                }
            }

            When("Lambda가 파비콘만 돌려주면") {
                val faviconKey = LinkPreviewFixture.key(kind = "favicon")
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(faviconKey = faviconKey))
                        service.fill(row.id!!)
                    }

                Then("favicon_url이 채워진다") {
                    fill()

                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe null
                    actualLink.faviconUrl shouldBe "$PUBLIC_BASE_URL/$faviconKey"
                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.DONE
                }
            }

            When("Lambda 호출이 실패하면") {
                val fill =
                    suspend {
                        client.extraction = LinkPreviewExtraction.Failed
                        service.fill(row.id!!)
                    }

                Then("행은 FAILED, 링크는 그대로다") {
                    fill()

                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.FAILED
                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe null
                    actualLink.faviconUrl shouldBe null
                    actualLink.isVideo shouldBe link.isVideo
                }
            }

            When("FAILED가 된 행을 다시 처리하면") {
                val fillTwice =
                    suspend {
                        client.extraction = LinkPreviewExtraction.Failed
                        service.fill(row.id!!)
                        service.fill(row.id!!)
                    }

                Then("Lambda를 호출하지 않는다") {
                    fillTwice()

                    client.calls.size shouldBe 1
                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.FAILED
                }
            }

            When("Lambda가 스로틀링을 알리면") {
                val fill =
                    suspend {
                        client.extraction = LinkPreviewExtraction.Throttled
                        service.fill(row.id!!)
                    }

                Then("행은 PENDING, started_at은 null, 링크는 그대로다") {
                    fill()

                    val actualRow = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actualRow.thumbnailStatus shouldBe ThumbnailStatus.PENDING
                    actualRow.startedAt shouldBe null
                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe null
                    actualLink.faviconUrl shouldBe null
                }
            }

            When("호출하는 동안 사용자가 썸네일을 넣었으면") {
                val userThumbnailUrl = RandomFixture.randomUrl()
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = {
                            linkRepository.updatePreview(
                                link.fillPreview(thumbnailUrl = userThumbnailUrl, faviconUrl = null, isVideo = false),
                            )
                        }
                        service.fill(row.id!!)
                    }

                Then("사용자 값이 덮어써지지 않고 행은 DONE이다") {
                    fill()

                    linkRepository.findById(link.id!!)?.thumbnailUrl shouldBe userThumbnailUrl
                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.DONE
                }
            }

            When("호출하는 동안 링크가 지워졌으면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = { linkRepository.deleteById(link.id!!) }
                        service.fill(row.id!!)
                    }

                Then("행은 STOPPED다") {
                    fill() shouldBe ThumbnailStatus.STOPPED

                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                }
            }

            When("호출하는 동안 가져오기가 되돌려졌으면") {
                lateinit var reversed: ImportLink
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = {
                            bookmarkImportRepository.reverseLinks(
                                importLinkIds = listOf(row.id!!),
                                reversedAt = RandomFixture.randomPastInstant(),
                            )
                            reversed = bookmarkImportRepository.findLinkById(row.id!!)!!
                        }
                        service.fill(row.id!!)
                    }

                Then("행과 링크가 그대로이고 결과는 null이다") {
                    fill() shouldBe null

                    val actualRow = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actualRow.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    actualRow.reversedAt shouldBe reversed.reversedAt
                    actualRow.startedAt shouldBe reversed.startedAt
                    val actualLink = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actualLink.thumbnailUrl shouldBe null
                    actualLink.faviconUrl shouldBe null
                }
            }

            When("같은 행을 동시에 두 번 처리하면") {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val fillConcurrently =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = {
                            entered.complete(Unit)
                            release.await()
                        }
                        coroutineScope {
                            val first = async { service.fill(row.id!!) }
                            entered.await()
                            val second = service.fill(row.id!!)
                            release.complete(Unit)
                            listOf(first.await(), second)
                        }
                    }

                Then("Lambda는 한 번만 호출된다") {
                    fillConcurrently()

                    client.calls.size shouldBe 1
                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.DONE
                }
            }
        }

        Given("실행 직전 확인에 걸리는 행") {
            beforeEach { client.reset() }

            When("링크가 지워졌으면") {
                lateinit var row: ImportLink
                val fill =
                    suspend {
                        val link = insertImportedLink()
                        row = insertImportRowOf(link = link)
                        linkRepository.deleteById(link.id!!)
                        service.fill(row.id!!)
                    }

                Then("행은 STOPPED, Lambda를 호출하지 않는다") {
                    fill()

                    bookmarkImportRepository.findLinkById(row.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    client.calls.size shouldBe 0
                }
            }

            When("링크에 썸네일이나 파비콘이 이미 있으면") {
                lateinit var links: List<Link>
                lateinit var rows: List<ImportLink>
                val fill =
                    suspend {
                        val user = userRepository.insert(UserFixture.createRandomValidUser())
                        links =
                            listOf(
                                LinkFixture.createRandomLinkOf(ownerId = user.id!!, thumbnailUrl = RandomFixture.randomUrl()),
                                LinkFixture.createRandomLinkOf(
                                    ownerId = user.id,
                                    thumbnailUrl = null,
                                    faviconUrl = RandomFixture.randomUrl(),
                                ),
                            ).map { linkRepository.insert(it) }
                        rows = links.map { insertImportRowOf(link = it) }
                        rows.forEach { service.fill(it.id!!) }
                    }

                Then("행은 DONE, 링크는 그대로, Lambda를 호출하지 않는다") {
                    fill()

                    rows.forEach { bookmarkImportRepository.findLinkById(it.id!!)?.thumbnailStatus shouldBe ThumbnailStatus.DONE }
                    links.forEach { link ->
                        val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                        actual.thumbnailUrl shouldBe link.thumbnailUrl
                        actual.faviconUrl shouldBe link.faviconUrl
                    }
                    client.calls.size shouldBe 0
                }
            }

            When("되돌리기로 STOPPED인 행이면") {
                lateinit var row: ImportLink
                val fill =
                    suspend {
                        row =
                            insertImportRowOf(
                                link = insertImportedLink(),
                                thumbnailStatus = ThumbnailStatus.STOPPED,
                                reversedAt = RandomFixture.randomPastInstant(),
                            )
                        service.fill(row.id!!)
                    }

                Then("그대로이고 Lambda를 호출하지 않는다") {
                    fill()

                    val actual = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actual.thumbnailStatus shouldBe ThumbnailStatus.STOPPED
                    actual.startedAt shouldBe null
                    client.calls.size shouldBe 0
                }
            }

            When("시작한 지 1시간이 안 된 PENDING 행이면") {
                lateinit var row: ImportLink
                val fill =
                    suspend {
                        val startedAt = dbNow() - RandomFixture.randomInt(1, 50).minutes
                        row = insertImportRowOf(link = insertImportedLink(), startedAt = startedAt)
                        service.fill(row.id!!)
                    }

                Then("건너뛰고 Lambda를 호출하지 않는다") {
                    fill()

                    val actual = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actual.thumbnailStatus shouldBe ThumbnailStatus.PENDING
                    actual.startedAt shouldBe row.startedAt
                    client.calls.size shouldBe 0
                }
            }

            When("시작한 지 1시간이 넘은 PENDING 행이면") {
                lateinit var row: ImportLink
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        val startedAt = dbNow() - ImportLink.THUMBNAIL_STALE_AFTER - RandomFixture.randomInt(10, 600).minutes
                        row = insertImportRowOf(link = insertImportedLink(), startedAt = startedAt)
                        service.fill(row.id!!)
                    }

                Then("다시 잡아 처리한다") {
                    fill()

                    val actual = bookmarkImportRepository.findLinkById(row.id!!).shouldNotBeNull()
                    actual.thumbnailStatus shouldBe ThumbnailStatus.DONE
                    actual.startedAt.shouldNotBeNull() shouldBeGreaterThan row.startedAt!!
                    client.calls.size shouldBe 1
                }
            }
        }
    })
