@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.service

import com.qlink.link.repository.LinkRepository
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
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class GetLinkContentScheduleServiceTest :
    BaseServiceTest({
        val service = koinGet<GetLinkContentScheduleService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()

        Given("due·미래·실행 중·끝난 행들") {
            lateinit var now: Instant
            var oldestDueAgeMinutes = 0
            var nextUnstartedInMinutes = 0
            var runningForMinutes = 0

            beforeEach {
                now = dbNow()
                oldestDueAgeMinutes = RandomFixture.randomInt(60, 600)
                nextUnstartedInMinutes = RandomFixture.randomInt(1, 60)
                runningForMinutes = RandomFixture.randomInt(1, LinkContent.STALE_AFTER.inWholeMinutes.toInt() - 1)
                val user = userRepository.insert(UserFixture.createRandomValidUser())

                suspend fun insert(content: (linkId: Long) -> LinkContent) {
                    val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                    linkContentRepository.insertAll(listOf(content(link.id!!)))
                }

                insert { LinkContentFixture.createPendingContentOf(linkId = it, nextAttemptAt = now - oldestDueAgeMinutes.minutes) }
                insert {
                    LinkContentFixture.createPendingContentOf(
                        linkId = it,
                        nextAttemptAt = now - RandomFixture.randomInt(1, 59).minutes,
                    )
                }
                insert { LinkContentFixture.createPendingContentOf(linkId = it, nextAttemptAt = now + nextUnstartedInMinutes.minutes) }
                insert {
                    LinkContentFixture.createPendingContentOf(
                        linkId = it,
                        nextAttemptAt = now + (nextUnstartedInMinutes + 30).minutes,
                    )
                }
                insert {
                    LinkContentFixture.createClaimedContentOf(
                        linkId = it,
                        startedAt = now - runningForMinutes.minutes,
                        nextAttemptAt = now - oldestDueAgeMinutes.minutes - 10.minutes,
                    )
                }
                insert { LinkContentFixture.createCollectedContentOf(linkId = it) }
                insert { LinkContentFixture.createClaimedContentOf(linkId = it).giveUp() }
            }

            When("기준 시각으로 읽으면") {
                val getSchedule = suspend { service.getSchedule(now) }

                Then("due 수·pending 수·다음 실행 가능 시각·가장 오래된 due 나이가 맞다") {
                    val schedule = getSchedule()

                    schedule.due shouldBe 2
                    schedule.pending shouldBe 5
                    schedule.nextRunnableAt shouldBe
                        minOf(now + nextUnstartedInMinutes.minutes, now - runningForMinutes.minutes + LinkContent.STALE_AFTER)
                    schedule.oldestDueAge shouldBe oldestDueAgeMinutes.minutes
                }
            }
        }
    })
