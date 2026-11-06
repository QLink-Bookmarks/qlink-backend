package com.qlink.linkcontent.service

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.qlink.common.text.hostOrNull
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.LinkContentConfig
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.client.LambdaLinkContentSyncClient
import com.qlink.linkcontent.domain.CollectOutcome
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentStatus
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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LinkContentLambdaIntegrationTest :
    BaseServiceTest({
        val localStackImage = "localstack/localstack:4.12.0"
        val localStackRuntime = "nodejs24.x"
        val localStack =
            LocalStackContainer(DockerImageName.parse(localStackImage))
                .withServices(LocalStackContainer.Service.LAMBDA)
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val linkContentRepository = koinGet<LinkContentRepository>()
        val tx = koinGet<TransactionRunner>()
        val text = LinkContentFixture.randomBodyText()
        val title = RandomFixture.randomSentenceWithMax(100)
        val siteName = RandomFixture.randomSentenceWithMax(50)
        val publishedAt = RandomFixture.randomPastInstant()
        val retryAfterSeconds =
            RandomFixture.randomInt(
                LinkContent.BACKOFF
                    .first()
                    .inWholeSeconds
                    .toInt() + 1,
                LinkContent.RETRY_AFTER_MAX.inWholeSeconds.toInt(),
            )
        lateinit var client: LambdaClient
        lateinit var fixtureZip: Path

        fun createFunction(
            name: String,
            zip: String,
            handler: String,
            vararg options: String,
        ) {
            val create =
                localStack.execInContainer(
                    "awslocal",
                    "lambda",
                    "create-function",
                    "--function-name",
                    name,
                    "--runtime",
                    localStackRuntime,
                    "--role",
                    "arn:aws:iam::000000000000:role/lambda-role",
                    "--handler",
                    handler,
                    "--zip-file",
                    "fileb://$zip",
                    *options,
                )
            create.exitCode shouldBe 0
            localStack
                .execInContainer("awslocal", "lambda", "wait", "function-active-v2", "--function-name", name)
                .exitCode shouldBe 0
        }

        fun serviceOf(functionName: String): CollectLinkContentService =
            CollectLinkContentService(
                tx = tx,
                linkContentRepository = linkContentRepository,
                linkRepository = linkRepository,
                contentClient =
                    LambdaLinkContentSyncClient(
                        lambdaClient = client,
                        config =
                            LinkContentConfig(
                                functionName = functionName,
                                invokeTimeout = 60.seconds,
                                workerEnabled = false,
                                workerConcurrency = 1,
                                workerParallelism = 1,
                                workerBatchSize = 1,
                                workerMaxPages = 1,
                                hostMinInterval = 0.milliseconds,
                                hostCooldown = 1.minutes,
                            ),
                        meterRegistry = SimpleMeterRegistry(),
                        log = LoggerFactory.getLogger(LambdaLinkContentSyncClient::class.java),
                    ),
            )

        suspend fun insertDueContentOf(
            url: String,
            now: Instant,
        ): LinkContent {
            val user = userRepository.insert(UserFixture.createRandomValidUser())
            val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, url = url))
            linkContentRepository.insertAll(
                listOf(
                    LinkContentFixture.createPendingContentOf(
                        linkId = link.id!!,
                        sourceUrl = url,
                        nextAttemptAt = now - RandomFixture.randomInt(1, 60).minutes,
                    ),
                ),
            )
            return linkContentRepository.findByLinkId(link.id).shouldNotBeNull()
        }

        fun urlOf(case: String): String = "https://site-${RandomFixture.randomId()}.example.com/$case"

        beforeSpec {
            localStack.start()
            fixtureZip = Files.createTempFile("link-content-fixture", ".zip")
            ZipOutputStream(Files.newOutputStream(fixtureZip)).use { zip ->
                zip.putNextEntry(ZipEntry("index.mjs"))
                zip.write(
                    """
                    const page = {
                      finalUrl: null, canonicalUrl: null, title: null, siteName: null, author: null, publishedAt: null,
                      language: null, text: null, truncation: 'NONE',
                    };
                    const failed = (outcome, code, upstreamStatus, retryAfterSeconds) =>
                      ({ outcome, code, upstreamStatus, retryAfterSeconds, ...page, durationMs: 12 });
                    export async function handler(event) {
                      const path = new URL(event.url).pathname;
                      if (path === '/error') throw new Error('fixture error');
                      if (path === '/collected') return {
                        outcome: 'COLLECTED', code: null, upstreamStatus: 200, retryAfterSeconds: null, ...page,
                        finalUrl: event.url, canonicalUrl: event.url, title: ${Json.encodeToString(
                        title,
                    )}, siteName: ${Json.encodeToString(siteName)},
                        author: null, publishedAt: '$publishedAt', language: 'ko', text: ${Json.encodeToString(text)},
                        truncation: 'TEXT', durationMs: 1800,
                      };
                      if (path === '/not-found') return failed('FAILED', 'UPSTREAM_NOT_FOUND', 404, null);
                      if (path === '/rate-limited') return failed('FAILED', 'UPSTREAM_RATE_LIMITED', 429, $retryAfterSeconds);
                      if (path === '/login') return failed('BLOCKED', 'LOGIN_REQUIRED', 200, null);
                      return failed('SKIPPED', null, null, null);
                    }
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            localStack.copyFileToContainer(MountableFile.forHostPath(fixtureZip), "/tmp/content-fixture.zip")
            createFunction(name = "content-fixture", zip = "/tmp/content-fixture.zip", handler = "index.handler")
            createFunction(name = "content-throttled", zip = "/tmp/content-fixture.zip", handler = "index.handler")
            localStack
                .execInContainer(
                    "awslocal",
                    "lambda",
                    "put-function-concurrency",
                    "--function-name",
                    "content-throttled",
                    "--reserved-concurrent-executions",
                    "0",
                ).exitCode shouldBe 0

            client =
                LambdaClient {
                    region = localStack.region
                    endpointUrl = Url.parse(localStack.endpoint.toString())
                    credentialsProvider = StaticCredentialsProvider(Credentials("test", "test"))
                }
        }

        afterSpec {
            runCatching { client.close() }
            runCatching { Files.deleteIfExists(fixtureZip) }
            localStack.stop()
        }

        Given("본문 Lambda 응답 JSON을 돌려주는 LocalStack Lambda와 PENDING이고 due인 행") {
            val service = serviceOf("content-fixture")
            lateinit var now: Instant

            beforeEach { now = dbNow() }

            When("Lambda가 COLLECTED 응답을 돌려주면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("collected"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("행이 COLLECTED이고 본문·제목·발췌가 저장된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.COLLECTED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.COLLECTED
                    actual.page?.text shouldBe text
                    actual.page?.title shouldBe title
                    actual.page?.siteName shouldBe siteName
                    actual.page?.publishedAt shouldBe publishedAt
                    actual.page?.language shouldBe "ko"
                    actual.page?.canonicalUrl shouldBe content.sourceUrl
                    actual.page?.finalUrl shouldBe content.sourceUrl
                    actual.page?.isTruncated shouldBe true
                    actual.excerpt shouldBe text.take(LinkContent.EXCERPT_MAX_CHARS) + "…"
                }
            }

            When("Lambda가 재시도하지 않는 코드의 FAILED 응답을 돌려주면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("not-found"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("행이 FAILED이고 실패 코드가 저장된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.FAILED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.FAILED
                    actual.failureCode shouldBe ContentFailureCode.UPSTREAM_NOT_FOUND
                }
            }

            When("Lambda가 UPSTREAM_RATE_LIMITED FAILED 응답을 돌려주면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("rate-limited"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("행이 retryAfter 뒤로 재시도 예약되고 결과에 호스트 쿨다운이 담긴다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RETRY_SCHEDULED
                    result.host shouldBe content.sourceUrl.hostOrNull()
                    result.cooldownSeconds shouldBe retryAfterSeconds
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.failureCode shouldBe ContentFailureCode.UPSTREAM_RATE_LIMITED
                    actual.nextAttemptAt shouldBe now + retryAfterSeconds.seconds
                }
            }

            When("Lambda가 BLOCKED 응답을 돌려주면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("login"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("행이 BLOCKED이고 차단 코드가 저장된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.BLOCKED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.BLOCKED
                    actual.failureCode shouldBe ContentFailureCode.LOGIN_REQUIRED
                }
            }

            When("Lambda가 알 수 없는 outcome을 돌려주면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("unknown"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("RELEASED_ERROR이고 행이 해제된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RELEASED_ERROR
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.startedAt.shouldBeNull()
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                }
            }

            When("Lambda 함수 오류가 나면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("error"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("RELEASED_ERROR이고 행이 해제된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RELEASED_ERROR
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.startedAt.shouldBeNull()
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                }
            }
        }

        Given("예약 동시성이 0인 LocalStack Lambda와 PENDING이고 due인 행") {
            val service = serviceOf("content-throttled")
            lateinit var now: Instant

            beforeEach { now = dbNow() }

            When("호출이 스로틀되면") {
                lateinit var content: LinkContent
                beforeEach {
                    content = insertDueContentOf(url = urlOf("collected"), now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("RELEASED_THROTTLED이고 행이 해제된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.RELEASED_THROTTLED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.PENDING
                    actual.startedAt.shouldBeNull()
                    actual.nextAttemptAt shouldBe now + LinkContent.RELEASE_DELAY
                }
            }
        }

        Given("실제 배포 ZIP의 본문 핸들러와 PENDING이고 due인 행") {
            val service = serviceOf("content-real")
            lateinit var now: Instant

            beforeEach { now = dbNow() }

            When("내부 주소 URL을 수집하면") {
                lateinit var content: LinkContent
                beforeEach {
                    val packagePath = Path.of("lambda/link-fetch/build/link-fetch.zip")
                    check(Files.exists(packagePath)) { "Build the Lambda ZIP with npm run package before running service tests" }
                    localStack.copyFileToContainer(MountableFile.forHostPath(packagePath), "/tmp/link-fetch.zip")
                    createFunction(
                        name = "content-real",
                        zip = "/tmp/link-fetch.zip",
                        handler = "content.handler",
                        "--environment",
                        "Variables={APP_ENV=dev}",
                    )
                    content = insertDueContentOf(url = "http://127.0.0.1/${RandomFixture.randomId()}", now = now)
                }
                val collect = suspend { service.collect(contentId = content.id!!, now = now) }

                Then("행이 FAILED이고 TARGET_DENIED가 저장된다") {
                    val result = collect()

                    result.outcome shouldBe CollectOutcome.FAILED
                    val actual = linkContentRepository.findById(content.id!!).shouldNotBeNull()
                    actual.status shouldBe ContentStatus.FAILED
                    actual.failureCode shouldBe ContentFailureCode.TARGET_DENIED
                }
            }
        }
    })
