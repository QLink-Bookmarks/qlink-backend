package com.qlink.linkcontent.domain.service

import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LinkContentPlanDomainServiceTest :
    BehaviorSpec({
        Given("저장된 링크와 그 본문 행") {
            val savedLink = LinkFixture.createRandomLinkOf(ownerId = RandomFixture.randomId())
            val now = RandomFixture.randomPastInstant()

            When("링크 URL이 행의 sourceUrl과 같으면") {
                val current = LinkContentFixture.createCollectedContentOf(linkId = savedLink.id!!, sourceUrl = savedLink.url)
                val planned = LinkContentPlanDomainService.forUrlChange(current = current, savedLink = savedLink, now = now)

                Then("null이다") {
                    planned.shouldBeNull()
                }
            }

            When("링크 URL이 다르면") {
                val current = LinkContentFixture.createCollectedContentOf(linkId = savedLink.id!!, sourceUrl = RandomFixture.randomUrl())
                val planned = LinkContentPlanDomainService.forUrlChange(current = current, savedLink = savedLink, now = now)

                Then("같은 id로 generation이 오른 PENDING 행이다") {
                    planned.shouldNotBeNull()
                    planned.id shouldBe current.id
                    planned.generation shouldBe current.generation + 1
                    planned.status shouldBe ContentStatus.PENDING
                    planned.sourceUrl shouldBe savedLink.url
                    planned.excerpt.shouldBeNull()
                }
            }

            When("행이 없으면") {
                val planned = LinkContentPlanDomainService.forUrlChange(current = null, savedLink = savedLink, now = now)

                Then("새 PENDING 행이다") {
                    planned.shouldNotBeNull()
                    planned.id.shouldBeNull()
                    planned.linkId shouldBe savedLink.id
                    planned.sourceUrl shouldBe savedLink.url
                    planned.status shouldBe ContentStatus.PENDING
                    planned.priority shouldBe ContentPriority.INTERACTIVE
                    planned.generation shouldBe 0
                    planned.nextAttemptAt shouldBe now
                }
            }
        }
    })
