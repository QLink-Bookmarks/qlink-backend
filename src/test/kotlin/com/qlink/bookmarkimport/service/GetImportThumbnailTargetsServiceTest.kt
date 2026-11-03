@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.dbNow
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class GetImportThumbnailTargetsServiceTest :
    BaseServiceTest({
        val service = koinGet<GetImportThumbnailTargetsService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()

        Given("실행 대상과 대상이 아닌 가져오기 링크 행") {
            lateinit var olderTargetLink: Link
            lateinit var newerTargetLink: Link

            beforeEach {
                val user = userRepository.insert(UserFixture.createRandomValidUser())
                val bookmarkImport = bookmarkImportRepository.insert(ImportFixture.createBookmarkImportOf(ownerId = user.id!!))
                val now = dbNow()

                suspend fun insertRow(
                    createdAt: Instant,
                    thumbnailStatus: ThumbnailStatus = ThumbnailStatus.PENDING,
                    startedAt: Instant? = null,
                ): Link {
                    val link = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id, createdAt = createdAt))
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
                    return link
                }

                olderTargetLink = insertRow(createdAt = now - 3.days)
                newerTargetLink =
                    insertRow(
                        createdAt = now - 1.days,
                        startedAt = now - ImportLink.THUMBNAIL_STALE_AFTER - RandomFixture.randomInt(10, 600).minutes,
                    )
                insertRow(createdAt = now - 2.days, startedAt = now - RandomFixture.randomInt(1, 50).minutes)
                listOf(ThumbnailStatus.DONE, ThumbnailStatus.FAILED, ThumbnailStatus.STOPPED).forEach { status ->
                    insertRow(createdAt = now - 2.days, thumbnailStatus = status)
                }
            }

            When("실행 대상을 읽으면") {
                val getTargets = suspend { service.getTargets(size = RandomFixture.randomInt(2, 100)) }

                Then("시작하지 않은 행과 시작한 지 1시간이 넘은 PENDING 행만 링크 created_at 내림차순으로, 지금 링크 URL과 함께 돌려준다") {
                    val targets = getTargets()

                    targets.map { it.url } shouldContainExactly listOf(newerTargetLink.url, olderTargetLink.url)
                    targets.map { bookmarkImportRepository.findLinkById(it.importLinkId)?.linkId } shouldContainExactly
                        listOf(newerTargetLink.id, olderTargetLink.id)
                }
            }

            When("묶음 크기가 대상 수보다 작으면") {
                val getTargets = suspend { service.getTargets(size = 1) }

                Then("가장 최신 링크의 행만 돌려준다") {
                    getTargets().map { it.url } shouldContainExactly listOf(newerTargetLink.url)
                }
            }
        }
    })
