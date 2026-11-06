package com.qlink.linkcontent.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ELLIPSIS = "…"

class LinkContentTest :
    BehaviorSpec({
        Given("새 링크의 본문 행을 만들 때") {
            val linkId = RandomFixture.randomId()
            val sourceUrl = RandomFixture.randomUrl()
            val priority = LinkContentFixture.randomPriority()
            val now = RandomFixture.randomPastInstant()

            When("링크 id·URL·우선순위로 만들면") {
                val content = LinkContent.create(linkId = linkId, sourceUrl = sourceUrl, priority = priority, now = now)

                Then("PENDING이고 nextAttemptAt이 지금이며 attempt·claimCount가 0이고 우선순위가 담긴다") {
                    content.status shouldBe ContentStatus.PENDING
                    content.linkId shouldBe linkId
                    content.sourceUrl shouldBe sourceUrl
                    content.nextAttemptAt shouldBe now
                    content.attempt shouldBe 0
                    content.claimCount shouldBe 0
                    content.priority shouldBe priority
                }
            }
        }

        Given("PENDING 행이 클레임된 상태") {
            val claimed = LinkContentFixture.createClaimedContentOf(linkId = RandomFixture.randomId())
            val now = RandomFixture.randomPastInstant()

            fun completeWith(text: String): LinkContent =
                claimed.complete(page = LinkContentFixture.createCollectedPage(text = text), now = now)

            When("정상 추출 결과를 받으면") {
                val page = LinkContentFixture.createCollectedPage()
                val completed = claimed.complete(page = page, now = now)

                Then("COLLECTED가 되고 본문·길이·해시·collectedAt·발췌가 채워진다") {
                    completed.status shouldBe ContentStatus.COLLECTED
                    completed.page shouldBe page
                    completed.contentLength shouldBe page.text.length
                    completed.contentHash.shouldNotBeNull().length shouldBe 64
                    completed.collectedAt shouldBe now
                    completed.excerpt.shouldNotBeNull()
                    completed.startedAt.shouldBeNull()
                }
            }

            When("본문이 100자 이상이면") {
                val text = LinkContentFixture.randomBodyText(RandomFixture.randomInt(101, 2_000))
                val completed = completeWith(text)

                Then("발췌는 앞 100자 뒤에 …가 붙고 길이가 101이다") {
                    completed.excerpt shouldBe text.take(100) + ELLIPSIS
                    completed.excerpt!!.length shouldBe 101
                }
            }

            When("본문이 정확히 100자이면") {
                val text = LinkContentFixture.randomBodyText(100)
                val completed = completeWith(text)

                Then("발췌는 100자 전체 뒤에 …가 붙는다") {
                    completed.excerpt shouldBe text + ELLIPSIS
                }
            }

            When("본문이 99자이면") {
                val text = LinkContentFixture.randomBodyText(99)
                val completed = completeWith(text)

                Then("발췌는 29자 + …이다") {
                    completed.excerpt shouldBe text.take(29) + ELLIPSIS
                }
            }

            When("본문이 50자이면") {
                val text = LinkContentFixture.randomBodyText(50)
                val completed = completeWith(text)

                Then("발췌는 15자 + …이다") {
                    completed.excerpt shouldBe text.take(15) + ELLIPSIS
                }
            }

            When("본문이 4자이면") {
                val text = LinkContentFixture.randomBodyText(4)
                val completed = completeWith(text)

                Then("발췌는 1자 + …이다") {
                    completed.excerpt shouldBe text.take(1) + ELLIPSIS
                }
            }

            When("본문이 3자 이하이면") {
                val completed = (1..3).map { completeWith(LinkContentFixture.randomBodyText(it)) }

                Then("COLLECTED이지만 발췌가 없다") {
                    completed.forEach {
                        it.status shouldBe ContentStatus.COLLECTED
                        it.excerpt.shouldBeNull()
                    }
                }
            }

            When("본문이 이모지로 시작하는 4자이면") {
                val emoji = "😀"
                val completed = completeWith(emoji + LinkContentFixture.randomBodyText(2))

                Then("예산 1자가 surrogate 쌍을 못 자르므로 발췌가 없다") {
                    completed.status shouldBe ContentStatus.COLLECTED
                    completed.excerpt.shouldBeNull()
                }
            }

            When("본문 100자 경계가 surrogate 쌍 중간이면") {
                val head = LinkContentFixture.randomBodyText(99)
                val text = head + "😀" + LinkContentFixture.randomBodyText(50)
                val completed = completeWith(text)

                Then("쌍이 깨지지 않고 짧게 잘린 뒤 …가 붙는다") {
                    completed.excerpt shouldBe head + ELLIPSIS
                }
            }

            When("본문에 줄바꿈·연속 공백이 있으면") {
                val words = (1..8).map { LinkContentFixture.randomBodyText(5) }
                val text = "  " + words.joinToString("\n\n   \t") + "\n"
                val normalized = words.joinToString(" ")
                val completed = completeWith(text)

                Then("발췌는 공백 한 칸으로 정규화된 한 덩어리이고 n은 정규화 뒤 길이다") {
                    normalized.length shouldBe 47
                    completed.excerpt shouldBe normalized.take(47 * 3 / 10).trimEnd() + ELLIPSIS
                }
            }

            When("재시도 가능한 코드로 첫 실패하면") {
                val failed = claimed.fail(code = ContentFailureCode.FETCH_TIMEOUT, retryAfterSeconds = null, now = now)

                Then("PENDING이고 attempt가 1이며 nextAttemptAt이 2분 뒤다") {
                    failed.status shouldBe ContentStatus.PENDING
                    failed.attempt shouldBe 1
                    failed.nextAttemptAt shouldBe now + 2.minutes
                    failed.startedAt.shouldBeNull()
                }
            }

            When("재시도 가능한 코드로 두 번째 실패하면") {
                val failed =
                    LinkContentFixture
                        .createClaimedContentOf(linkId = RandomFixture.randomId(), attempt = 1)
                        .fail(code = ContentFailureCode.UPSTREAM_SERVER_ERROR, retryAfterSeconds = null, now = now)

                Then("PENDING이고 attempt가 2이며 nextAttemptAt이 15분 뒤다") {
                    failed.status shouldBe ContentStatus.PENDING
                    failed.attempt shouldBe 2
                    failed.nextAttemptAt shouldBe now + 15.minutes
                }
            }

            When("재시도 가능한 코드로 세 번째 실패하면") {
                val failed =
                    LinkContentFixture
                        .createClaimedContentOf(linkId = RandomFixture.randomId(), attempt = 2)
                        .fail(code = ContentFailureCode.FETCH_TIMEOUT, retryAfterSeconds = null, now = now)

                Then("FAILED가 되고 실패 코드가 남는다") {
                    failed.status shouldBe ContentStatus.FAILED
                    failed.failureCode shouldBe ContentFailureCode.FETCH_TIMEOUT
                    failed.attempt shouldBe 3
                }
            }

            When("429이고 Retry-After가 backoff보다 크면") {
                val failed = claimed.fail(code = ContentFailureCode.UPSTREAM_RATE_LIMITED, retryAfterSeconds = 600, now = now)

                Then("nextAttemptAt이 Retry-After 뒤다") {
                    failed.nextAttemptAt shouldBe now + 600.seconds
                }
            }

            When("429이고 Retry-After가 900초를 넘으면") {
                val failed =
                    claimed.fail(
                        code = ContentFailureCode.UPSTREAM_RATE_LIMITED,
                        retryAfterSeconds = RandomFixture.randomInt(901, 100_000),
                        now = now,
                    )

                Then("900초로 잘린다") {
                    failed.nextAttemptAt shouldBe now + 900.seconds
                }
            }

            When("429이고 Retry-After가 backoff보다 작으면") {
                val failed =
                    claimed.fail(
                        code = ContentFailureCode.UPSTREAM_RATE_LIMITED,
                        retryAfterSeconds = RandomFixture.randomInt(0, 120),
                        now = now,
                    )

                Then("backoff가 적용된다") {
                    failed.nextAttemptAt shouldBe now + 2.minutes
                }
            }

            When("재시도 불가 코드로 실패하면") {
                val failed = claimed.fail(code = ContentFailureCode.NOT_HTML, retryAfterSeconds = null, now = now)

                Then("바로 FAILED가 된다") {
                    failed.status shouldBe ContentStatus.FAILED
                    failed.failureCode shouldBe ContentFailureCode.NOT_HTML
                    failed.attempt shouldBe claimed.attempt
                }
            }

            When("차단 코드를 받으면") {
                val failed = claimed.fail(code = ContentFailureCode.LOGIN_REQUIRED, retryAfterSeconds = null, now = now)

                Then("BLOCKED가 된다") {
                    failed.status shouldBe ContentStatus.BLOCKED
                    failed.failureCode shouldBe ContentFailureCode.LOGIN_REQUIRED
                }
            }

            When("release하면") {
                val released = claimed.release(now)

                Then("PENDING이고 attempt는 그대로이며 startedAt이 비고 nextAttemptAt이 1분 뒤다") {
                    released.status shouldBe ContentStatus.PENDING
                    released.attempt shouldBe claimed.attempt
                    released.startedAt.shouldBeNull()
                    released.nextAttemptAt shouldBe now + 1.minutes
                }
            }

            When("포기하면") {
                val givenUp = claimed.giveUp()

                Then("FAILED(WORKER_GIVE_UP)이다") {
                    givenUp.status shouldBe ContentStatus.FAILED
                    givenUp.failureCode shouldBe ContentFailureCode.WORKER_GIVE_UP
                }
            }
        }

        Given("Retry-After 초") {
            When("0초에서 900초 사이이면") {
                val seconds = RandomFixture.randomInt(0, 900)

                Then("그대로다") {
                    LinkContent.clampRetryAfter(seconds) shouldBe seconds
                }
            }

            When("900초를 넘으면") {
                val seconds = RandomFixture.randomInt(901, 100_000)

                Then("900초다") {
                    LinkContent.clampRetryAfter(seconds) shouldBe 900
                }
            }

            When("음수이면") {
                val seconds = RandomFixture.randomNegative()

                Then("0초다") {
                    LinkContent.clampRetryAfter(seconds) shouldBe 0
                }
            }
        }

        Given("COLLECTED 행의 필수 필드") {
            When("본문이 없으면") {
                val create = {
                    LinkContentFixture.createContentWithExcerpt(status = ContentStatus.COLLECTED, text = null, excerpt = null)
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_PAGE_REQUIRED.message) { create() }
                }
            }

            When("본문 해시가 없으면") {
                val text = LinkContentFixture.randomBodyText(100)
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = text,
                        excerpt = text + ELLIPSIS,
                        contentHash = null,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_HASH_REQUIRED.message) { create() }
                }
            }

            When("수집 시각이 없으면") {
                val text = LinkContentFixture.randomBodyText(100)
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = text,
                        excerpt = text + ELLIPSIS,
                        collectedAt = null,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_COLLECTED_AT_REQUIRED.message) { create() }
                }
            }
        }

        Given("어떤 상태의 행") {
            val newUrl = RandomFixture.randomUrl()
            val now = RandomFixture.randomPastInstant()

            When("COLLECTED 행을 새 URL로 초기화하면") {
                val collected = LinkContentFixture.createCollectedContentOf(linkId = RandomFixture.randomId())
                val reset = collected.resetForUrl(sourceUrl = newUrl, now = now)

                Then("id는 같고 PENDING이며 본문·발췌·메타가 비고 nextAttemptAt이 지금이다") {
                    reset.id shouldBe collected.id
                    reset.status shouldBe ContentStatus.PENDING
                    reset.sourceUrl shouldBe newUrl
                    reset.nextAttemptAt shouldBe now
                    reset.page.shouldBeNull()
                    reset.excerpt.shouldBeNull()
                    reset.contentHash.shouldBeNull()
                    reset.collectedAt.shouldBeNull()
                }
            }

            When("재시도 대기 중인 행을 새 URL로 초기화하면") {
                val retrying =
                    LinkContentFixture
                        .createClaimedContentOf(linkId = RandomFixture.randomId(), attempt = RandomFixture.randomInt(0, 1))
                        .fail(code = ContentFailureCode.FETCH_TIMEOUT, retryAfterSeconds = null, now = now)
                val reset = retrying.resetForUrl(sourceUrl = newUrl, now = now)

                Then("attempt·claimCount·실패 코드가 초기값이다") {
                    reset.attempt shouldBe 0
                    reset.claimCount shouldBe 0
                    reset.failureCode.shouldBeNull()
                }
            }

            When("클레임된 BULK 행을 새 URL로 초기화하면") {
                val claimed = LinkContentFixture.createClaimedContentOf(linkId = RandomFixture.randomId(), priority = ContentPriority.BULK)
                val reset = claimed.resetForUrl(sourceUrl = newUrl, now = now)

                Then("startedAt이 비고 claimCount가 0이며 INTERACTIVE가 된다") {
                    reset.startedAt.shouldBeNull()
                    reset.claimCount shouldBe 0
                    reset.priority shouldBe ContentPriority.INTERACTIVE
                }
            }

            When("COLLECTED인데 발췌를 만들 본문이고 발췌가 없으면") {
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = LinkContentFixture.randomBodyText(),
                        excerpt = null,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_EXCERPT_REQUIRED.message) { create() }
                }
            }

            When("발췌가 …로 끝나지 않으면") {
                val text = LinkContentFixture.randomBodyText()
                val create = {
                    LinkContentFixture.createContentWithExcerpt(status = ContentStatus.COLLECTED, text = text, excerpt = text.take(100))
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_EXCERPT_SUFFIX_INVALID.message) { create() }
                }
            }

            When("발췌 길이가 101을 넘으면") {
                val text = LinkContentFixture.randomBodyText(RandomFixture.randomInt(101, 2_000))
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = text,
                        excerpt = text.take(101) + ELLIPSIS,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_EXCERPT_OVER_MAX.message) { create() }
                }
            }

            When("발췌의 … 앞 길이가 본문 예산을 넘으면") {
                val text = LinkContentFixture.randomBodyText(50)
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = text,
                        excerpt = text.take(16) + ELLIPSIS,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_EXCERPT_OVER_BUDGET.message) { create() }
                }
            }

            When("COLLECTED인데 발췌를 만들 수 없는 3자 본문에 …만 있는 발췌가 있으면") {
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = LinkContentFixture.randomBodyText(3),
                        excerpt = ELLIPSIS,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_EXCERPT_NOT_ALLOWED.message) { create() }
                }
            }

            When("COLLECTED가 아닌데 본문이 있으면") {
                val create = {
                    LinkContentFixture.createContentWithExcerpt(
                        status = listOf(ContentStatus.PENDING, ContentStatus.FAILED, ContentStatus.BLOCKED).random(),
                        text = LinkContentFixture.randomBodyText(),
                        excerpt = null,
                        contentHash = null,
                        collectedAt = null,
                    )
                }

                Then("생성이 거부된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_COLLECTED_FIELDS_NOT_ALLOWED.message) { create() }
                }
            }

            When("COLLECTED이고 발췌 형식이 맞으면") {
                val text = LinkContentFixture.randomBodyText(50)
                val content =
                    LinkContentFixture.createContentWithExcerpt(
                        status = ContentStatus.COLLECTED,
                        text = text,
                        excerpt = text.take(15) + ELLIPSIS,
                    )

                Then("만들어진다") {
                    content.excerpt.shouldNotBeNull() shouldEndWith ELLIPSIS
                }
            }
        }
    })
