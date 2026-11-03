package com.qlink.ai.domain

import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AiJobTest :
    BehaviorSpec({
        Given("상태가 정해진 AI 작업") {
            val requestedAt = RandomFixture.randomPastInstant()

            When("P면") {
                val job = AiFixture.createAiJobWith(requestedAt = requestedAt, status = AiJobStatus.P)

                Then("isPending은 true") {
                    job.isPending shouldBe true
                }
            }

            When("C나 F면") {
                val jobs =
                    listOf(AiJobStatus.C, AiJobStatus.F).map { status ->
                        AiFixture.createAiJobWith(requestedAt = requestedAt, status = status)
                    }

                Then("isPending은 false") {
                    jobs.forEach { it.isPending shouldBe false }
                }
            }
        }

        Given("실행 대상인지 판단할 AI 작업") {
            val now = RandomFixture.randomPastInstant()
            val withinStale = AiJob.STALE_AFTER - RandomFixture.randomInt(1, 59).minutes
            val overStale = AiJob.STALE_AFTER + RandomFixture.randomInt(1, 3600).seconds

            When("P이고 시작한 적 없으며 요청한 지 1시간 미만이면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - withinStale)

                Then("RUN") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.RUN
                }
            }

            When("P이고 시작한 적 없으며 요청한 지 정확히 1시간이면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - AiJob.STALE_AFTER)

                Then("RUN") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.RUN
                }
            }

            When("P이고 시작한 적 없으며 요청한 지 1시간을 넘었으면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - overStale)

                Then("FAIL") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.FAIL
                }
            }

            When("P이고 시작한 지 1시간 미만이면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - overStale, startedAt = now - withinStale)

                Then("SKIP") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.SKIP
                }
            }

            When("P이고 시작한 지 정확히 1시간이면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - overStale, startedAt = now - AiJob.STALE_AFTER)

                Then("SKIP") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.SKIP
                }
            }

            When("P이고 시작한 지 1시간을 넘었으면") {
                val job = AiFixture.createAiJobWith(requestedAt = now - overStale, startedAt = now - overStale)

                Then("FAIL(다시 실행하지 않음)") {
                    job.recoveryAction(now) shouldBe AiJobRecoveryAction.FAIL
                }
            }

            When("C나 F면") {
                val jobs =
                    listOf(AiJobStatus.C, AiJobStatus.F).map { status ->
                        AiFixture.createAiJobWith(requestedAt = now - overStale, status = status, startedAt = now - overStale)
                    }

                Then("SKIP") {
                    jobs.forEach { it.recoveryAction(now) shouldBe AiJobRecoveryAction.SKIP }
                }
            }
        }
    })
