@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.service

import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.dbNow
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class GetLinkContentTargetsServiceTest :
    BaseServiceTest({
        val service = koinGet<GetLinkContentTargetsService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()

        Given("due·미래·실행 중·stale·끝난 행이 섞여 있을 때") {
            lateinit var now: Instant
            lateinit var sweepStartedAt: Instant
            lateinit var expectedIds: List<Long>

            beforeEach {
                now = dbNow()
                sweepStartedAt = now - RandomFixture.randomInt(5, 10).minutes
                val user = userRepository.insert(UserFixture.createRandomValidUser())

                suspend fun insert(content: (linkId: Long) -> LinkContent): Long {
                    val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    linkContentRepository.insertAll(listOf(content(link.id!!)))
                    return linkContentRepository.findByLinkId(link.id).shouldNotBeNull().id!!
                }

                val bulkOldest =
                    insert {
                        LinkContentFixture.createPendingContentOf(
                            linkId = it,
                            priority = ContentPriority.BULK,
                            nextAttemptAt =
                                now - 5.hours,
                        )
                    }
                val interactiveNewer =
                    insert {
                        LinkContentFixture.createPendingContentOf(
                            linkId = it,
                            priority = ContentPriority.INTERACTIVE,
                            nextAttemptAt = sweepStartedAt - 1.hours,
                        )
                    }
                val interactiveOlder =
                    insert {
                        LinkContentFixture.createPendingContentOf(
                            linkId = it,
                            priority = ContentPriority.INTERACTIVE,
                            nextAttemptAt = sweepStartedAt - 2.hours,
                        )
                    }
                val interactiveStale =
                    insert {
                        LinkContentFixture.createClaimedContentOf(
                            linkId = it,
                            priority = ContentPriority.INTERACTIVE,
                            startedAt = now - LinkContent.STALE_AFTER - RandomFixture.randomInt(1, 60).minutes,
                            nextAttemptAt = sweepStartedAt - 3.hours,
                        )
                    }
                // 대상이 아닌 행: sweep 시작 뒤 due, 미래, 실행 중, 끝난 행
                insert { LinkContentFixture.createPendingContentOf(linkId = it, nextAttemptAt = sweepStartedAt + 1.minutes) }
                insert {
                    LinkContentFixture.createPendingContentOf(
                        linkId = it,
                        nextAttemptAt = now + RandomFixture.randomInt(1, 60).minutes,
                    )
                }
                insert {
                    LinkContentFixture.createClaimedContentOf(
                        linkId = it,
                        startedAt = now - RandomFixture.randomInt(1, 9).minutes,
                        nextAttemptAt = sweepStartedAt - 4.hours,
                    )
                }
                insert { LinkContentFixture.createCollectedContentOf(linkId = it) }
                insert { LinkContentFixture.createClaimedContentOf(linkId = it).giveUp() }

                expectedIds = listOf(interactiveStale, interactiveOlder, interactiveNewer, bulkOldest)
            }

            When("sweep 시작 시각과 keyset으로 읽으면") {
                val getTargets = suspend { service.getTargets(size = 3, dueBefore = sweepStartedAt, after = null, now = now) }

                Then("due이고 미실행(또는 stale)인 행만 우선순위·due·id 순으로 size만큼 나온다") {
                    getTargets().map { it.contentId } shouldContainExactly expectedIds.take(3)
                }
            }

            When("마지막 행의 keyset 뒤를 읽으면") {
                val getNextTargets =
                    suspend {
                        val first = service.getTargets(size = 2, dueBefore = sweepStartedAt, after = null, now = now)
                        service.getTargets(size = 100, dueBefore = sweepStartedAt, after = first.last().keyset, now = now)
                    }

                Then("그 뒤 행만 나온다") {
                    getNextTargets().map { it.contentId } shouldContainExactly expectedIds.drop(2)
                }
            }
        }
    })
