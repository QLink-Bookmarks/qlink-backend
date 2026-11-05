@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.domain.service

import com.qlink.linkcontent.domain.CollectAction
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi

class LinkContentCollectDomainServiceTest :
    BehaviorSpec({
        Given("클레임된 행") {
            val link = LinkFixture.createRandomLinkOf(ownerId = RandomFixture.randomId())
            val now = RandomFixture.randomPastInstant()

            When("링크가 없으면") {
                val claimed = LinkContentFixture.createClaimedContentOf(linkId = link.id!!, sourceUrl = link.url)
                val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = null, now = now)

                Then("LINK_GONE이다") {
                    preparation.action shouldBe CollectAction.LINK_GONE
                    preparation.next.shouldBeNull()
                }
            }

            When("클레임 횟수가 6을 넘으면") {
                val claimed =
                    LinkContentFixture.createClaimedContentOf(
                        linkId = link.id!!,
                        sourceUrl = link.url,
                        claimCount = LinkContent.MAX_CLAIMS + RandomFixture.randomInt(1, 5),
                    )
                val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = link, now = now)

                Then("GIVE_UP이고 다음 상태가 FAILED(WORKER_GIVE_UP)이다") {
                    preparation.action shouldBe CollectAction.GIVE_UP
                    val next = preparation.next.shouldNotBeNull()
                    next.status shouldBe ContentStatus.FAILED
                    next.failureCode shouldBe ContentFailureCode.WORKER_GIVE_UP
                }
            }

            When("링크 URL이 sourceUrl과 다르면") {
                val claimed = LinkContentFixture.createClaimedContentOf(linkId = link.id!!, sourceUrl = RandomFixture.randomUrl())
                val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = link, now = now)

                Then("URL_CHANGED이고 다음 상태가 release된 PENDING이다") {
                    preparation.action shouldBe CollectAction.URL_CHANGED
                    val next = preparation.next.shouldNotBeNull()
                    next.status shouldBe ContentStatus.PENDING
                    next.claimToken.shouldBeNull()
                    next.startedAt.shouldBeNull()
                    next.attempt shouldBe claimed.attempt
                    next.nextAttemptAt shouldBe now + 1.minutes
                }
            }

            When("그 외이면") {
                val claimed = LinkContentFixture.createClaimedContentOf(linkId = link.id!!, sourceUrl = link.url)
                val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = link, now = now)

                Then("COLLECT이다") {
                    preparation.action shouldBe CollectAction.COLLECT
                    preparation.next shouldBe claimed
                }
            }
        }
    })
