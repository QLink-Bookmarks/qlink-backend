package com.qlink.link.service

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.model.InvokeRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.LinkPreviewConfig
import com.qlink.link.client.LambdaLinkPreviewSyncClient
import com.qlink.link.client.LinkPreviewMode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LinkPreviewLambdaIntegrationTest :
    BaseServiceTest({
        val localStackImage = "localstack/localstack:4.12.0"
        val localStackRuntime = "nodejs24.x"
        val localStack =
            LocalStackContainer(DockerImageName.parse(localStackImage))
                .withServices(LocalStackContainer.Service.LAMBDA, LocalStackContainer.Service.S3)
        val userRepository = koinGet<UserRepository>()
        val tx = koinGet<TransactionRunner>()
        lateinit var client: LambdaClient
        lateinit var previewClient: LambdaLinkPreviewSyncClient
        lateinit var service: CreateLinkPreviewService
        lateinit var fixtureZip: Path
        val key = "dev-preview-${"a".repeat(64)}.png"
        val badKey = "prod-preview-${"c".repeat(64)}.png"

        beforeSpec {
            localStack.start()
            fixtureZip = Files.createTempFile("link-preview-fixture", ".zip")
            ZipOutputStream(Files.newOutputStream(fixtureZip)).use { zip ->
                zip.putNextEntry(ZipEntry("index.mjs"))
                zip.write(
                    """
                    import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3';
                    const s3 = new S3Client({ endpoint: process.env.AWS_ENDPOINT_URL, forcePathStyle: true });
                    export async function handler(event) {
                      if (event.url.includes('error')) throw new Error('fixture error');
                      if (event.url.includes('bad-key')) return { title: null, thumbnailKey: '$badKey', faviconKey: null, isVideo: false };
                      if (event.mode === 'FAVICON_ONLY' && !event.favicon?.base64) throw new Error('missing favicon bytes');
                      const key = event.mode === 'FAVICON_ONLY'
                        ? 'dev-favicon-${"b".repeat(64)}.png'
                        : 'dev-preview-${"a".repeat(64)}.png';
                      await s3.send(new PutObjectCommand({
                        Bucket: 'alink-previews-dev', Key: key,
                        Body: event.favicon ? Buffer.from(event.favicon.base64, 'base64') : Buffer.from([137,80,78,71]),
                        ContentType: 'image/png', CacheControl: 'public, max-age=86400'
                      }));
                      return { title: 'Fixture', thumbnailKey: event.favicon ? null : key, faviconKey: event.favicon ? key : null, isVideo: false };
                    }
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            localStack.copyFileToContainer(MountableFile.forHostPath(fixtureZip), "/tmp/preview-fixture.zip")
            val createBucket = localStack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "alink-previews-dev")
            check(createBucket.exitCode == 0) { createBucket.stderr + createBucket.stdout }
            val createFunction =
                localStack.execInContainer(
                    "awslocal",
                    "lambda",
                    "create-function",
                    "--function-name",
                    "preview-fixture",
                    "--runtime",
                    localStackRuntime,
                    "--role",
                    "arn:aws:iam::000000000000:role/lambda-role",
                    "--handler",
                    "index.handler",
                    "--zip-file",
                    "fileb:///tmp/preview-fixture.zip",
                )
            check(createFunction.exitCode == 0) { createFunction.stderr + createFunction.stdout }
            localStack
                .execInContainer("awslocal", "lambda", "wait", "function-active-v2", "--function-name", "preview-fixture")
                .exitCode shouldBe 0

            client =
                LambdaClient {
                    region = localStack.region
                    endpointUrl = Url.parse(localStack.endpoint.toString())
                    credentialsProvider = StaticCredentialsProvider(Credentials("test", "test"))
                }
            val config =
                LinkPreviewConfig(
                    environment = "dev",
                    functionName = "preview-fixture",
                    publicBaseUrl = "https://images.archivelink.app",
                    region = localStack.region,
                    endpoint = localStack.endpoint.toString(),
                )
            previewClient = LambdaLinkPreviewSyncClient(lambdaClient = client, config = config)
            service =
                CreateLinkPreviewService(
                    tx = tx,
                    userRepository = userRepository,
                    previewClient = previewClient,
                    config = config,
                )
        }

        afterSpec {
            runCatching { client.close() }
            runCatching { Files.deleteIfExists(fixtureZip) }
            localStack.stop()
        }

        Given("LocalStack Lambda와 S3") {
            lateinit var user: User
            beforeTest { user = userRepository.insert(UserFixture.createRandomValidUser()) }

            When("실제 Lambda 클라이언트로 프리뷰를 요청하면") {
                val create = suspend { service.createPreview(user.id!!, RandomFixture.randomUrl()) }

                Then("CDN URL과 Lambda가 쓴 S3 객체 메타데이터가 일치한다") {
                    val response = create()
                    response.thumbnailUrl shouldBe "https://images.archivelink.app/$key"
                    response.faviconUrl shouldBe null
                    val head =
                        localStack.execInContainer(
                            "awslocal",
                            "s3api",
                            "head-object",
                            "--bucket",
                            "alink-previews-dev",
                            "--key",
                            key,
                        )
                    head.exitCode shouldBe 0
                    head.stdout shouldContain "image/png"
                    head.stdout shouldContain "public, max-age=86400"
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("Lambda 실행 오류가 나면") {
                val create = suspend { service.createPreview(user.id!!, "https://example.com/error") }

                Then("빈 프리뷰를 반환한다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe null
                    response.isVideo shouldBe false
                }
            }

            When("Lambda가 다른 환경의 키를 반환하면") {
                val create = suspend { service.createPreview(user.id!!, "https://example.com/bad-key") }

                Then("공개 URL을 만들지 않는다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe null
                }
            }

            When("favicon 바이트를 전달하면") {
                Then("Lambda가 바이트를 받고 favicon 객체 키를 반환한다") {
                    val bytes = byteArrayOf(137.toByte(), 80, 78, 71)
                    val result =
                        previewClient.extract(
                            url = "https://example.com/favicon",
                            mode = LinkPreviewMode.FAVICON_ONLY,
                            faviconBytes = bytes,
                        )
                    result?.faviconKey shouldBe "dev-favicon-${"b".repeat(64)}.png"
                    val head =
                        localStack.execInContainer(
                            "awslocal",
                            "s3api",
                            "head-object",
                            "--bucket",
                            "alink-previews-dev",
                            "--key",
                            result!!.faviconKey!!,
                        )
                    head.exitCode shouldBe 0
                    head.stdout shouldContain "image/png"
                }
            }
        }

        Given("실제 배포 ZIP") {
            When("Linux에서 Lambda를 기동하면") {
                Then("핸들러와 네이티브 이미지 처리 라이브러리로 실제 favicon을 저장한다") {
                    val packagePath = Path.of("lambda/link-preview/build/link-preview.zip")
                    check(Files.exists(packagePath)) { "Build the Lambda ZIP with npm run package before running service tests" }
                    localStack.copyFileToContainer(MountableFile.forHostPath(packagePath), "/tmp/link-preview.zip")
                    localStack
                        .execInContainer(
                            "awslocal",
                            "lambda",
                            "create-function",
                            "--function-name",
                            "preview-real",
                            "--runtime",
                            localStackRuntime,
                            "--role",
                            "arn:aws:iam::000000000000:role/lambda-role",
                            "--handler",
                            "index.handler",
                            "--zip-file",
                            "fileb:///tmp/link-preview.zip",
                            "--environment",
                            "Variables={APP_ENV=dev,PREVIEW_BUCKET=alink-previews-dev}",
                        ).exitCode shouldBe 0
                    localStack
                        .execInContainer("awslocal", "lambda", "wait", "function-active-v2", "--function-name", "preview-real")
                        .exitCode shouldBe 0
                    val response =
                        client.invoke(
                            InvokeRequest {
                                functionName = "preview-real"
                                payload = "{\"url\":\"file:///etc/passwd\",\"mode\":\"PREVIEW\"}".encodeToByteArray()
                            },
                        )
                    response.functionError shouldBe null
                    response.payload?.decodeToString() shouldContain "thumbnailKey"
                    val png =
                        Base64.getDecoder().decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==",
                        )
                    val realUrl = "https://real-example.com/favicon"
                    val encodedPng = Base64.getEncoder().encodeToString(png)
                    val faviconResponse =
                        client.invoke(
                            InvokeRequest {
                                functionName = "preview-real"
                                payload =
                                    """{"url":"$realUrl","mode":"FAVICON_ONLY","favicon":{"base64":"$encodedPng"}}"""
                                        .encodeToByteArray()
                            },
                        )
                    faviconResponse.functionError shouldBe null
                    val hash =
                        MessageDigest
                            .getInstance("SHA-256")
                            .digest("real-example.com".encodeToByteArray())
                            .joinToString("") { "%02x".format(it) }
                    val realKey = "dev-favicon-$hash.png"
                    faviconResponse.payload?.decodeToString() shouldContain realKey
                    val head =
                        localStack.execInContainer(
                            "awslocal",
                            "s3api",
                            "head-object",
                            "--bucket",
                            "alink-previews-dev",
                            "--key",
                            realKey,
                        )
                    head.exitCode shouldBe 0
                    head.stdout shouldContain "image/png"
                }
            }
        }
    })
