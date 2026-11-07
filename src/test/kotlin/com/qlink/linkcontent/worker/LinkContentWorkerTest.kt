package com.qlink.linkcontent.worker

import com.qlink.config.LinkContentConfig
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.service.CollectLinkContentService
import com.qlink.linkcontent.service.GetLinkContentScheduleService
import com.qlink.linkcontent.service.GetLinkContentTargetsService
import com.qlink.support.BaseServiceTest
import com.qlink.support.ServiceTestEnvironment
import com.qlink.support.dbNow
import com.qlink.support.fixture.FakeLinkContentSyncClient
import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LinkContentWorkerTest :
    BaseServiceTest({
        val client = koinGet<FakeLinkContentSyncClient>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()

        fun createWorker(): LinkContentWorker =
            LinkContentWorker(
                signal = LinkContentSignal(),
                getTargetsService = koinGet<GetLinkContentTargetsService>(),
                getScheduleService = koinGet<GetLinkContentScheduleService>(),
                collectService = koinGet<CollectLinkContentService>(),
                config =
                    LinkContentConfig(
                        functionName = RandomFixture.randomUrl(),
                        invokeTimeout = 45.seconds,
                        workerEnabled = false,
                        workerConcurrency = 3,
                        workerParallelism = 2,
                        workerBatchSize = 100,
                        workerMaxPages = 5,
                        hostMinInterval = Duration.ZERO,
                        hostCooldown = 1.minutes,
                    ),
                pollInterval = 10.minutes,
                meterRegistry = SimpleMeterRegistry(),
                traceIdLength = ServiceTestEnvironment.TRACE_ID_LENGTH,
                log = LoggerFactory.getLogger(LinkContentWorker::class.java),
            )

        fun randomHost(): String = URI(RandomFixture.randomUrl()).host

        suspend fun insertPendingLinks(
            ownerId: Long,
            host: String,
            count: Int,
            priority: ContentPriority,
            nextAttemptAt: Instant,
        ): List<Link> =
            List(count) { index ->
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(ownerId = ownerId, url = "https://$host/$index-${RandomFixture.randomId()}"),
                    )
                linkContentRepository.insertAll(
                    listOf(
                        LinkContentFixture.createPendingContentOf(
                            linkId = link.id!!,
                            sourceUrl = link.url,
                            priority = priority,
                            nextAttemptAt = nextAttemptAt,
                        ),
                    ),
                )
                link
            }

        suspend fun statusesOf(links: List<Link>): List<ContentStatus?> = links.map { linkContentRepository.findByLinkId(it.id!!)?.status }

        Given("한 호스트에 몰린 BULK 행 15개가 한 페이지로 due일 때") {
            lateinit var owner: User
            lateinit var bulkLinks: List<Link>
            lateinit var worker: LinkContentWorker

            beforeEach {
                client.reset()
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                bulkLinks =
                    insertPendingLinks(
                        ownerId = owner.id!!,
                        host = randomHost(),
                        count = 15,
                        priority = ContentPriority.BULK,
                        nextAttemptAt = dbNow() - 1.hours,
                    )
                worker = createWorker()
            }

            afterEach {
                worker.stop()
            }

            When("BULK를 처리하는 중에 INTERACTIVE 행이 저장되면") {
                lateinit var interactiveLinks: List<Link>
                val drain =
                    suspend {
                        client.enqueue(*Array(bulkLinks.size + 1) { LinkContentFixture.collectedResult() })
                        client.onInvoke = {
                            client.onInvoke = {}
                            interactiveLinks =
                                insertPendingLinks(
                                    ownerId = owner.id!!,
                                    host = randomHost(),
                                    count = 1,
                                    priority = ContentPriority.INTERACTIVE,
                                    nextAttemptAt = Clock.System.now(),
                                )
                        }
                        worker.drain()
                    }

                Then("10행 뒤에 INTERACTIVE 행을 먼저 수집하고 남은 BULK 행을 이어서 수집한다") {
                    drain()

                    client.calls shouldContainExactly
                        bulkLinks.take(10).map { it.url } + interactiveLinks.map { it.url } + bulkLinks.drop(10).map { it.url }
                    statusesOf(bulkLinks + interactiveLinks) shouldContainOnly listOf(ContentStatus.COLLECTED)
                }
            }

            When("기다리는 INTERACTIVE 행이 없으면") {
                val drain =
                    suspend {
                        client.enqueue(*Array(bulkLinks.size) { LinkContentFixture.collectedResult() })
                        worker.drain()
                    }

                Then("BULK 행을 keyset 순서대로 한 번씩 수집한다") {
                    drain()

                    client.calls shouldContainExactly bulkLinks.map { it.url }
                    statusesOf(bulkLinks) shouldContainOnly listOf(ContentStatus.COLLECTED)
                }
            }
        }

        Given("같은 호스트의 INTERACTIVE 행과 BULK 행이 함께 due일 때") {
            lateinit var bulkLinks: List<Link>
            lateinit var interactiveLinks: List<Link>
            lateinit var worker: LinkContentWorker

            beforeEach {
                client.reset()
                val ownerId = userRepository.insert(UserFixture.createRandomValidUser()).id!!
                val host = randomHost()
                val now = dbNow()
                bulkLinks =
                    insertPendingLinks(
                        ownerId = ownerId,
                        host = host,
                        count = 3,
                        priority = ContentPriority.BULK,
                        nextAttemptAt = now - 2.hours,
                    )
                interactiveLinks =
                    insertPendingLinks(
                        ownerId = ownerId,
                        host = host,
                        count = 2,
                        priority = ContentPriority.INTERACTIVE,
                        nextAttemptAt = now - 1.hours,
                    )
                worker = createWorker()
            }

            afterEach {
                worker.stop()
            }

            When("한 번 처리하면") {
                val drain =
                    suspend {
                        client.enqueue(*Array(bulkLinks.size + interactiveLinks.size) { LinkContentFixture.collectedResult() })
                        worker.drain()
                    }

                Then("INTERACTIVE 행을 먼저 수집한 뒤 BULK 행을 수집한다") {
                    drain()

                    client.calls shouldContainExactly (interactiveLinks + bulkLinks).map { it.url }
                    statusesOf(interactiveLinks + bulkLinks) shouldContainOnly listOf(ContentStatus.COLLECTED)
                }
            }
        }

        Given("BULK 행 60개가 한 페이지로 due이고 다른 호스트가 429로 쿨다운 중일 때") {
            lateinit var owner: User
            lateinit var coolingHost: String
            lateinit var bulkLinks: List<Link>
            lateinit var worker: LinkContentWorker

            beforeEach {
                client.reset()
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                coolingHost = randomHost()
                worker = createWorker()
                insertPendingLinks(
                    ownerId = owner.id!!,
                    host = coolingHost,
                    count = 1,
                    priority = ContentPriority.INTERACTIVE,
                    nextAttemptAt = dbNow() - 1.hours,
                )
                client.enqueue(LinkContentFixture.failedResult(code = ContentFailureCode.UPSTREAM_RATE_LIMITED, upstreamStatus = 429))
                worker.drain()
                client.reset()
                bulkLinks =
                    insertPendingLinks(
                        ownerId = owner.id!!,
                        host = randomHost(),
                        count = 60,
                        priority = ContentPriority.BULK,
                        nextAttemptAt = dbNow() - 1.hours,
                    )
            }

            afterEach {
                worker.stop()
            }

            When("BULK를 처리하는 중에 쿨다운 중인 호스트의 INTERACTIVE 행이 저장되면") {
                lateinit var interactiveLinks: List<Link>
                val drain =
                    suspend {
                        client.enqueue(*Array(bulkLinks.size) { LinkContentFixture.collectedResult() })
                        client.onInvoke = {
                            client.onInvoke = {}
                            interactiveLinks =
                                insertPendingLinks(
                                    ownerId = owner.id!!,
                                    host = coolingHost,
                                    count = 1,
                                    priority = ContentPriority.INTERACTIVE,
                                    nextAttemptAt = Clock.System.now(),
                                )
                        }
                        worker.drain()
                    }

                Then("BULK 페이지를 멈추지 않고 끝까지 수집하며 INTERACTIVE 행은 PENDING으로 남는다") {
                    drain()

                    client.calls shouldContainExactly bulkLinks.map { it.url }
                    statusesOf(bulkLinks) shouldContainOnly listOf(ContentStatus.COLLECTED)
                    statusesOf(interactiveLinks) shouldContainOnly listOf(ContentStatus.PENDING)
                }
            }
        }
    })
