@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.service

import com.qlink.common.text.hostOrNull
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.client.LinkContentExtraction
import com.qlink.linkcontent.domain.CollectOutcome
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.dbNow
import com.qlink.support.fixture.FakeLinkContentSyncClient
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class CollectLinkContentServiceTest :
    BaseServiceTest({
        val service = koinGet<CollectLinkContentService>()
        val client = koinGet<FakeLinkContentSyncClient>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()

        suspend fun insertLink(): Link {
            val user = userRepository.insert(UserFixture.createRandomValidUser())
            return linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
        }

        suspend fun insertDueContentOf(
            link: Link,
            now: Instant,
            sourceUrl: String = link.url,
        ): LinkContent {
            linkContentRepository.insertAll(
                listOf(
                    LinkContentFixture.createPendingContentOf(
                        linkId = link.id!!,
                        sourceUrl = sourceUrl,
                        nextAttemptAt = now - RandomFixture.randomInt(1, 60).minutes,
                    ),
                ),
            )
            return linkContentRepository.findByLinkId(link.id).shouldNotBeNull()
        }

        suspend fun resetForUrl(
            linkId: Long,
            url: String,
            now: Instant,
        ) {
            val current = linkContentRepository.findByLinkId(linkId).shouldNotBeNull()
            linkContentRepository.upsertForLink(current.resetForUrl(sourceUrl = url, now = now))
        }

        Given("PENDING이고 due인 행과 그 링크, 성공을 돌려주는 가짜 클라이언트") {
            lateinit var now: Instant
            lateinit var link: Link
            lateinit var content: LinkContent

            beforeEach {
                client.reset()
                now = dbNow()
                link = insertLink()
                content = insertDueContentOf(link = link, now = now)
            }

            When("수집하면") {
                val text = LinkContentFixture.randomBodyText()
                val collect =
                    suspend {
                        client.enqueue(LinkContentFixture.collectedResult(text = text))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행이 COLLECTED이고 발췌가 저장되며 links.updatedAt은 그대로다") {
                    val before = linkRepository.findById(link.id!!).shouldNotBeNull()

                    val result = collect()

                    result.outcome shouldBe CollectOutcome.COLLECTED
                    result.status shouldBe ContentStatus.COLLECTED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.COLLECTED
                    actual.page?.text shouldBe text
                    actual.collectedAt shouldBe now
                    actual.excerpt shouldBe text.take(LinkContent.EXCERPT_MAX_CHARS) + "…"
                    actual.claimToken.shouldBeNull()
                    linkRepository.findById(link.id!!)?.updatedAt shouldBe before.updatedAt
                    client.calls shouldBe listOf(link.url)
                }
            }

            When("같은 행을 같은 시각에 다시 수집하면") {
                val collectTwice =
                    suspend {
                        client.enqueue(LinkContentFixture.collectedResult(), LinkContentFixture.collectedResult())
                        service.collect(contentId = content.id!!, now = now)
                        val afterFirst = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                        afterFirst to service.collect(contentId = content.id!!, now = now)
                    }

                Then("클레임에 실패해 SKIPPED이고 행이 바뀌지 않는다") {
                    val (afterFirst, second) = collectTwice()

                    second.outcome shouldBe CollectOutcome.SKIPPED
                    second.status.shouldBeNull()
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe afterFirst.status
                    actual.page?.text shouldBe afterFirst.page?.text
                    actual.excerpt shouldBe afterFirst.excerpt
                    actual.claimCount shouldBe afterFirst.claimCount
                    client.calls.size shouldBe 1
                }
            }

            When("50자 본문을 돌려주면") {
                val text = LinkContentFixture.randomBodyText(length = 50)
                val collect =
                    suspend {
                        client.enqueue(LinkContentFixture.collectedResult(text = text))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행의 발췌가 15자 + …이다") {
                    collect()

                    linkContentRepository.findById(content.id!!)?.excerpt shouldBe text.take(15) + "…"
                }
            }

            When("199자 본문을 돌려주면") {
                val text = LinkContentFixture.randomBodyText(length = 199)
                val collect =
                    suspend {
                        client.enqueue(LinkContentFixture.collectedResult(text = text))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행의 발췌가 100자 + …이다") {
                    collect()

                    linkContentRepository.findById(content.id!!)?.excerpt shouldBe text.take(100) + "…"
                }
            }

            When("3자 본문을 돌려주면") {
                val text = LinkContentFixture.randomBodyText(length = 3)
                val collect =
                    suspend {
                        client.enqueue(LinkContentFixture.collectedResult(text = text))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행이 COLLECTED이고 발췌가 없다") {
                    collect()

                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.COLLECTED
                    actual.page?.text shouldBe text
                    actual.excerpt.shouldBeNull()
                }
            }
        }

        Given("PENDING이고 due인 행과 그 링크, 실패를 돌려주는 가짜 클라이언트") {
            lateinit var now: Instant
            lateinit var link: Link
            lateinit var content: LinkContent

            beforeEach {
                client.reset()
                now = dbNow()
                link = insertLink()
                content = insertDueContentOf(link = link, now = now)
            }

            When("재시도 가능한 실패이면") {
                val code = listOf(ContentFailureCode.FETCH_TIMEOUT, ContentFailureCode.UPSTREAM_SERVER_ERROR).random()
                val collect =
                    suspend {
                        client.enqueue(LinkContentFixture.failedResult(code = code))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행이 PENDING이고 attempt가 1, 결과가 RETRY_SCHEDULED이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RETRY_SCHEDULED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.attempt shouldBe 1
                    actual.nextAttemptAt shouldBe now + LinkContent.BACKOFF.first()
                    actual.claimToken.shouldBeNull()
                    actual.startedAt.shouldBeNull()
                    result.host.shouldBeNull()
                }
            }

            When("429이고 Retry-After가 있으면") {
                val retryAfterSeconds = RandomFixture.randomInt(121, 2_000)
                val collect =
                    suspend {
                        client.enqueue(
                            LinkContentFixture.failedResult(
                                code = ContentFailureCode.UPSTREAM_RATE_LIMITED,
                                upstreamStatus = 429,
                                retryAfterSeconds = retryAfterSeconds,
                            ),
                        )
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("결과에 호스트와 쿨다운 초가 담기고 행의 nextAttemptAt이 Retry-After(0~900초로 잘림) 뒤다") {
                    val result = collect()

                    val clamped = minOf(retryAfterSeconds, LinkContent.RETRY_AFTER_MAX.inWholeSeconds.toInt())
                    result.outcome shouldBe CollectOutcome.RETRY_SCHEDULED
                    result.host shouldBe (link.url.hostOrNull() ?: link.url)
                    result.cooldownSeconds shouldBe clamped
                    linkContentRepository.findById(content.id!!)?.nextAttemptAt shouldBe now + clamped.seconds
                }
            }

            When("차단 코드이면") {
                val code = ContentFailureCode.entries.filter { it.isBlocking }.random()
                val collect =
                    suspend {
                        client.enqueue(LinkContentExtraction.Blocked(code = code))
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행이 BLOCKED이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.BLOCKED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.BLOCKED
                    actual.failureCode shouldBe code
                }
            }

            When("throttled이면") {
                val collect =
                    suspend {
                        client.enqueue(LinkContentExtraction.Throttled)
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("행이 release되어 PENDING, attempt는 그대로, RELEASED_THROTTLED이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RELEASED_THROTTLED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.attempt shouldBe content.attempt
                    actual.claimToken.shouldBeNull()
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                }
            }

            When("응답이 잘못됐으면") {
                val collect =
                    suspend {
                        client.enqueue(LinkContentExtraction.Invalid)
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("release되고 RELEASED_ERROR이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RELEASED_ERROR
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.attempt shouldBe content.attempt
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                }
            }
        }

        Given("클레임 횟수가 6에 이른 stale 행") {
            lateinit var now: Instant
            lateinit var content: LinkContent

            beforeEach {
                client.reset()
                now = dbNow()
                val link = insertLink()
                val startedAt = now - LinkContent.STALE_AFTER - RandomFixture.randomInt(1, 60).minutes
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createClaimedContentOf(
                            linkId = link.id!!,
                            sourceUrl = link.url,
                            claimCount = LinkContent.MAX_CLAIMS,
                            startedAt = startedAt,
                        ),
                    ),
                )
                content = linkContentRepository.findByLinkId(link.id).shouldNotBeNull()
            }

            When("수집하면") {
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("클라이언트를 부르지 않고 FAILED(WORKER_GIVE_UP)이 저장된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.GIVE_UP
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.FAILED
                    actual.failureCode shouldBe ContentFailureCode.WORKER_GIVE_UP
                    client.calls.shouldBeEmpty()
                }
            }
        }

        Given("가짜 클라이언트의 호출 중 훅으로 끼어드는 변경") {
            lateinit var now: Instant
            lateinit var link: Link
            lateinit var content: LinkContent

            beforeEach {
                client.reset()
                now = dbNow()
                link = insertLink()
                content = insertDueContentOf(link = link, now = now)
                client.enqueue(LinkContentFixture.collectedResult())
            }

            When("호출 중 링크가 삭제되면") {
                val collect =
                    suspend {
                        client.onInvoke = { linkRepository.deleteById(link.id!!) }
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("완료 저장이 0행이라 SKIPPED이고 행이 없다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.SKIPPED
                    linkContentRepository.findById(content.id!!).shouldBeNull()
                }
            }

            When("호출 중 URL reset으로 generation이 오르면") {
                val newUrl = RandomFixture.randomUrl()
                val collect =
                    suspend {
                        client.onInvoke = { resetForUrl(linkId = link.id!!, url = newUrl, now = now) }
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("이전 실행의 완료가 버려지고 행은 새 generation의 PENDING이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.SKIPPED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.generation shouldBe content.generation + 1
                    actual.sourceUrl shouldBe newUrl
                    actual.page.shouldBeNull()
                    actual.excerpt.shouldBeNull()
                }
            }

            When("호출 중 A→B→A로 URL이 두 번 바뀌면") {
                val otherUrl = RandomFixture.randomUrl()
                val collect =
                    suspend {
                        client.onInvoke = {
                            resetForUrl(linkId = link.id!!, url = otherUrl, now = now)
                            resetForUrl(linkId = link.id!!, url = link.url, now = now)
                        }
                        service.collect(contentId = content.id!!, now = now)
                    }

                Then("첫 실행의 완료가 버려진다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.SKIPPED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.generation shouldBe content.generation + 2
                    actual.sourceUrl shouldBe link.url
                    actual.page.shouldBeNull()
                }
            }

            When("호출 중 stale 기준 시각이 지나 다른 실행이 재클레임하면") {
                val collect =
                    suspend {
                        var reclaimedToken: Any? = null
                        client.onInvoke = {
                            val reclaimedAt = now + LinkContent.STALE_AFTER + RandomFixture.randomInt(1, 60).minutes
                            reclaimedToken =
                                linkContentRepository
                                    .claim(contentId = content.id!!, staleAfter = LinkContent.STALE_AFTER, now = reclaimedAt)
                                    .shouldNotBeNull()
                                    .claimToken
                        }
                        service.collect(contentId = content.id!!, now = now) to reclaimedToken
                    }

                Then("이전 실행의 완료는 토큰이 달라 버려진다") {
                    val (result, reclaimedToken) = collect()

                    result.outcome shouldBe CollectOutcome.SKIPPED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.page.shouldBeNull()
                    actual.claimToken shouldNotBe null
                    actual.claimToken shouldBe reclaimedToken
                    actual.claimCount shouldBe content.claimCount + 2
                }
            }
        }

        Given("링크 URL과 다른 sourceUrl의 PENDING 행") {
            lateinit var now: Instant
            lateinit var content: LinkContent

            beforeEach {
                client.reset()
                now = dbNow()
                val link = insertLink()
                content = insertDueContentOf(link = link, now = now, sourceUrl = RandomFixture.randomUrl())
            }

            When("수집하면") {
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("클라이언트를 부르지 않고 release되어 URL_CHANGED이다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.URL_CHANGED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.claimToken.shouldBeNull()
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                    client.calls.shouldBeEmpty()
                }
            }
        }
    })
