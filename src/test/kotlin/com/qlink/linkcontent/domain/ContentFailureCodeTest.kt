package com.qlink.linkcontent.domain

import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe

class ContentFailureCodeTest :
    BehaviorSpec({
        Given("실패 코드") {
            val retryable =
                listOf(
                    ContentFailureCode.FETCH_TIMEOUT,
                    ContentFailureCode.UPSTREAM_SERVER_ERROR,
                    ContentFailureCode.UPSTREAM_RATE_LIMITED,
                )
            val blocking =
                listOf(
                    ContentFailureCode.LOGIN_REQUIRED,
                    ContentFailureCode.PAYWALL,
                    ContentFailureCode.BOT_WALL,
                    ContentFailureCode.ACCESS_DENIED,
                    ContentFailureCode.ROBOTS_NOINDEX,
                )

            When("FETCH_TIMEOUT·UPSTREAM_SERVER_ERROR·UPSTREAM_RATE_LIMITED이면") {
                val retryableFlags = retryable.map { it.isRetryable }
                val otherFlags = ContentFailureCode.entries.filterNot { it in retryable }.map { it.isRetryable }

                Then("isRetryable이 참이고, 그 외(NOT_HTML·CONTENT_EMPTY·NOT_CONTENT·UPSTREAM_NOT_FOUND 등)는 거짓이다") {
                    retryableFlags shouldContainOnly listOf(true)
                    otherFlags shouldContainOnly listOf(false)
                }
            }

            When("LOGIN_REQUIRED·PAYWALL·BOT_WALL·ACCESS_DENIED·ROBOTS_NOINDEX이면") {
                val blockingFlags = blocking.map { it.isBlocking }
                val otherFlags = ContentFailureCode.entries.filterNot { it in blocking }.map { it.isBlocking }

                Then("isBlocking이 참이고 그 외는 거짓이다") {
                    blockingFlags shouldContainOnly listOf(true)
                    otherFlags shouldContainOnly listOf(false)
                }
            }

            When("알려진 문자열이면") {
                val code = ContentFailureCode.entries.random()

                Then("그 코드다") {
                    ContentFailureCode.from(code.name) shouldBe code
                }
            }

            When("알 수 없는 문자열이면") {
                val unknown = "UNLISTED_${RandomFixture.randomId()}"

                Then("UNKNOWN이다") {
                    ContentFailureCode.from(unknown) shouldBe ContentFailureCode.UNKNOWN
                    ContentFailureCode.from(null) shouldBe ContentFailureCode.UNKNOWN
                }
            }
        }
    })
