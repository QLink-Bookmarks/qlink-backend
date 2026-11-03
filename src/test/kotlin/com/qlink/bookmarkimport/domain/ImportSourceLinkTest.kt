package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ImportSourceLinkTest :
    BehaviorSpec({
        Given("원본 링크 항목") {
            val now = Clock.System.now()

            When("URL이 정확히 2,048자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH - prefix.length)
                val reason = ImportFixture.createSourceLink(url = url).failureReason()

                Then("실패 아님") {
                    url shouldHaveLength ImportSource.LINK_URL_MAX_LENGTH
                    reason shouldBe null
                }
            }

            When("URL이 2,049자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH + 1 - prefix.length)
                val reason = ImportFixture.createSourceLink(url = url).failureReason()

                Then("URL_INVALID") {
                    reason shouldBe ImportFailureReason.URL_INVALID
                }
            }

            When("제목이 공백뿐이면") {
                val reason = ImportFixture.createSourceLink(title = "  ").failureReason()

                Then("TITLE_INVALID") {
                    reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("제목이 정확히 300자면") {
                val title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH)
                val reason = ImportFixture.createSourceLink(title = title).failureReason()

                Then("실패 아님") {
                    reason shouldBe null
                }
            }

            When("제목이 301자면") {
                val reason =
                    ImportFixture
                        .createSourceLink(title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH + 1))
                        .failureReason()

                Then("TITLE_INVALID") {
                    reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("createdAt이 과거면") {
                val createdAt = RandomFixture.randomPastInstant()
                val resolved = ImportFixture.createSourceLink(createdAt = createdAt).createdAtOr(now)

                Then("그 값") {
                    resolved shouldBe createdAt
                }
            }

            When("createdAt이 null이면") {
                val resolved = ImportFixture.createSourceLink(createdAt = null).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }

            When("createdAt이 저장 시각보다 미래면") {
                val resolved = ImportFixture.createSourceLink(createdAt = now + 1.days).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }
        }
    })
