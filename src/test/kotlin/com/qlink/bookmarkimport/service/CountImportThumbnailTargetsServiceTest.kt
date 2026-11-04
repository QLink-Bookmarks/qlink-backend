@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.dbNow
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class CountImportThumbnailTargetsServiceTest :
    BaseServiceTest({
        val service = koinGet<CountImportThumbnailTargetsService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()

        Given("실행 대상과 대상이 아닌 가져오기 링크 행") {
            var targetCount = 0

            beforeEach {
                val user = userRepository.insert(UserFixture.createRandomValidUser())
                val bookmarkImport = bookmarkImportRepository.insert(ImportFixture.createBookmarkImportOf(ownerId = user.id!!))
                val now = dbNow()
                val neverStartedCount = RandomFixture.randomInt(1, 5)
                val staleCount = RandomFixture.randomInt(1, 5)
                targetCount = neverStartedCount + staleCount

                suspend fun insertRow(
                    thumbnailStatus: ThumbnailStatus = ThumbnailStatus.PENDING,
                    startedAt: Instant? = null,
                ) {
                    val link = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id))
                    bookmarkImportRepository.insertLinks(
                        listOf(
                            ImportFixture.createImportLinkOf(
                                importId = bookmarkImport.id!!,
                                linkId = link.id!!,
                                thumbnailStatus = thumbnailStatus,
                                startedAt = startedAt,
                            ),
                        ),
                    )
                }

                repeat(neverStartedCount) { insertRow() }
                repeat(staleCount) {
                    insertRow(startedAt = now - ImportLink.THUMBNAIL_STALE_AFTER - RandomFixture.randomInt(10, 600).minutes)
                }
                insertRow(startedAt = now - RandomFixture.randomInt(1, 50).minutes)
                listOf(ThumbnailStatus.DONE, ThumbnailStatus.FAILED, ThumbnailStatus.STOPPED).forEach { insertRow(thumbnailStatus = it) }
            }

            When("대기 수를 세면") {
                val count = suspend { service.count() }

                Then("시작하지 않은 PENDING 행과 시작한 지 1시간이 넘은 PENDING 행만 센다") {
                    count() shouldBe targetCount.toLong()
                }
            }
        }
    })
