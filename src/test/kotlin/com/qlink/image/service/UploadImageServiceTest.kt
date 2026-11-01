package com.qlink.image.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.S3Config
import com.qlink.image.domain.ImagePurpose
import com.qlink.image.domain.ImageType
import com.qlink.image.storage.S3ImageStorage
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.support.BaseServiceTest
import com.qlink.support.ServiceTestEnvironment
import com.qlink.support.fixture.FakeLinkPreviewSyncClient
import com.qlink.support.fixture.ImageFixture
import com.qlink.support.fixture.LinkPreviewFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.net.URI

private val FILE_PURPOSES = listOf(ImagePurpose.PROFILE, ImagePurpose.POST, ImagePurpose.LINK_PREVIEW)

class UploadImageServiceTest :
    BaseServiceTest({
        val uploadImageService = koinGet<UploadImageService>()
        val userRepository = koinGet<UserRepository>()
        val tx = koinGet<TransactionRunner>()
        val s3Client = koinGet<S3Client>()
        val s3Config = koinGet<S3Config>()
        val previewClient = koinGet<FakeLinkPreviewSyncClient>()

        fun serviceOf(config: S3Config): UploadImageService =
            UploadImageService(
                tx = tx,
                userRepository = userRepository,
                imageStorage = S3ImageStorage(s3Client = s3Client, config = config),
                previewClient = previewClient,
                urlResolver = PreviewImageUrlResolver(config = config),
            )

        fun S3Config.bucketOf(purpose: ImagePurpose): String =
            when (purpose) {
                ImagePurpose.PROFILE -> profileBucket
                ImagePurpose.POST -> postBucket
                else -> previewBucket
            }

        fun S3Config.keyOf(url: String): String = url.removePrefix("${publicBaseUrl.trimEnd('/')}/")

        suspend fun S3Config.contentTypeOf(
            purpose: ImagePurpose,
            key: String,
        ): String? =
            s3Client
                .headObject {
                    bucket = bucketOf(purpose)
                    this.key = key
                }.contentType

        suspend fun shouldKeep(user: User) {
            val actual = userRepository.findById(user.id!!)
            actual?.avatarUrl shouldBe user.avatarUrl
            actual?.updatedAt shouldBe user.updatedAt
        }

        lateinit var user: User

        beforeTest {
            previewClient.reset()
            user = userRepository.insert(UserFixture.createRandomValidUser())
        }

        Given("존재하는 로그인 사용자와 정상적인 이미지") {
            listOf("dev", "prod").forEach { environment ->
                When("${environment}에서 세 파일 목적을 각각 업로드하면") {
                    val config = ServiceTestEnvironment.s3Config(environment = environment)
                    val type = ImageType.entries.random()
                    val upload =
                        suspend {
                            FILE_PURPOSES.associateWith { purpose ->
                                serviceOf(config)
                                    .upload(
                                        loginId = user.id!!,
                                        purpose = RandomFixture.randomCase(purpose.name),
                                        url = null,
                                        bytes = ImageFixture.validImage(type),
                                    ).url
                            }
                        }

                    Then("목적별 $environment 버킷에 올바른 키·Content-Type의 객체가 있고 사용자 DB 상태는 유지된다") {
                        val urls = upload()
                        val kinds =
                            mapOf(
                                ImagePurpose.PROFILE to "profile",
                                ImagePurpose.POST to "post",
                                ImagePurpose.LINK_PREVIEW to "preview",
                            )

                        urls.forEach { (purpose, url) ->
                            val key = config.keyOf(url)
                            url shouldBe "${config.publicBaseUrl}/$key"
                            key shouldMatch Regex("^$environment-${kinds.getValue(purpose)}-[0-9a-f]{32}\\.${type.extension}$")
                            config.contentTypeOf(purpose = purpose, key = key) shouldBe type.contentType
                        }
                        shouldKeep(user)
                    }
                }
            }

            When("같은 파일을 두 번 업로드하면") {
                val purpose = FILE_PURPOSES.random()
                val bytes = ImageFixture.validImage()
                val upload =
                    suspend {
                        List(2) {
                            uploadImageService.upload(loginId = user.id!!, purpose = purpose.name, url = null, bytes = bytes).url
                        }
                    }

                Then("서로 다른 키의 객체 두 개가 있고 사용자 DB 상태는 유지된다") {
                    val keys = upload().map(s3Config::keyOf)

                    keys[0] shouldNotBe keys[1]
                    keys.forEach { key -> s3Config.contentTypeOf(purpose = purpose, key = key) shouldNotBe null }
                    shouldKeep(user)
                }
            }

            When("S3 저장에 실패하면") {
                val missingBucket = "alink-missing-${RandomFixture.randomId()}"
                val failingConfig = s3Config.copy(profileBucket = missingBucket, postBucket = missingBucket, previewBucket = missingBucket)
                val upload =
                    suspend {
                        serviceOf(failingConfig).upload(
                            loginId = user.id!!,
                            purpose = FILE_PURPOSES.random().name,
                            url = null,
                            bytes = ImageFixture.validImage(),
                        )
                    }

                Then("이미지 업로드 실패 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_FAILED.message) {
                        upload()
                    }
                }
            }
        }

        Given("존재하는 로그인 사용자가 파비콘을 요청할 때") {
            val pageUrl = RandomFixture.randomUrl()

            fun requestFavicon(
                url: String? = pageUrl,
                bytes: ByteArray? = null,
            ) = suspend {
                uploadImageService.upload(
                    loginId = user.id!!,
                    purpose = RandomFixture.randomCase(ImagePurpose.FAVICON.name),
                    url = url,
                    bytes = bytes,
                )
            }

            When("유효한 페이지 URL을 전달하면") {
                val key = LinkPreviewFixture.key(kind = "favicon", environment = s3Config.environment)
                val upload =
                    suspend {
                        previewClient.result = LinkPreviewResult(faviconKey = key)
                        requestFavicon()()
                    }

                Then("파일 바이트 없이 FAVICON_ONLY로 호출하고 현재 환경의 파비콘 URL을 반환하며 사용자 DB 상태는 유지된다") {
                    val response = upload()

                    response.url shouldBe "${s3Config.publicBaseUrl}/$key"
                    previewClient.calls shouldContainExactly listOf(pageUrl to LinkPreviewMode.FAVICON_ONLY)
                    previewClient.faviconBytes shouldContainExactly listOf(null)
                    shouldKeep(user)
                }
            }

            When("파일 파트도 전달하면") {
                val upload = requestFavicon(bytes = ImageFixture.validImage())

                Then("이미지 파일 불가 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_NOT_ALLOWED.message) {
                        upload()
                    }
                }
            }

            When("URL을 생략하면") {
                val upload = requestFavicon(url = null)

                Then("페이지 URL 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_URL_INVALID.message) {
                        upload()
                    }
                }
            }

            When("URL이 절대 HTTP(S)·호스트·userinfo 규칙을 어기면") {
                val host = URI(pageUrl).host
                val uploads =
                    listOf(
                        "/${RandomFixture.randomId()}",
                        "ftp://$host/${RandomFixture.randomId()}",
                        "https:///${RandomFixture.randomId()}",
                        "https://user@$host/${RandomFixture.randomId()}",
                    ).map { requestFavicon(url = it) }

                Then("페이지 URL 예외를 반환한다") {
                    uploads.forEach { upload ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_URL_INVALID.message) {
                            upload()
                        }
                    }
                }
            }

            When("Lambda 호출이 실패하면") {
                val throwing =
                    suspend {
                        previewClient.failure = IllegalStateException(RandomFixture.randomSentenceWithMax(50))
                        requestFavicon()()
                    }
                val empty =
                    suspend {
                        previewClient.failure = null
                        previewClient.result = null
                        requestFavicon()()
                    }

                Then("이미지 업로드 실패 예외를 반환한다") {
                    listOf(throwing, empty).forEach { upload ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_FAILED.message) {
                            upload()
                        }
                    }
                }
            }

            When("Lambda 결과에 파비콘 키가 없으면") {
                val upload =
                    suspend {
                        previewClient.result = LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview"))
                        requestFavicon()()
                    }

                Then("이미지 업로드 실패 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_FAILED.message) {
                        upload()
                    }
                }
            }

            When("Lambda 키의 환경이나 형식이 잘못되면") {
                val otherEnvironment = listOf("dev", "prod").first { it != s3Config.environment }
                val uploads =
                    listOf(
                        LinkPreviewFixture.key(kind = "favicon", environment = otherEnvironment),
                        LinkPreviewFixture.key(kind = "preview", environment = s3Config.environment),
                        LinkPreviewFixture.key(kind = "favicon", environment = s3Config.environment, extension = "ico"),
                        "${s3Config.environment}-favicon-${RandomFixture.randomId()}.png",
                    ).map { key ->
                        suspend {
                            previewClient.result = LinkPreviewResult(faviconKey = key)
                            requestFavicon()()
                        }
                    }

                Then("이미지 업로드 실패 예외를 반환한다") {
                    uploads.forEach { upload ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_FAILED.message) {
                            upload()
                        }
                    }
                }
            }
        }

        Given("존재하는 로그인 사용자가 파일 목적을 요청할 때") {
            When("url도 전달하면") {
                val upload =
                    suspend {
                        uploadImageService.upload(
                            loginId = user.id!!,
                            purpose = FILE_PURPOSES.random().name,
                            url = RandomFixture.randomUrl(),
                            bytes = ImageFixture.validImage(),
                        )
                    }

                Then("잘못된 요청 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
                        upload()
                    }
                }
            }
        }

        Given("존재하지 않는 로그인 사용자") {
            When("업로드를 요청하면") {
                val upload =
                    suspend {
                        uploadImageService.upload(
                            loginId = RandomFixture.randomId(),
                            purpose = ImagePurpose.entries.random().name,
                            url = null,
                            bytes = ImageFixture.validImage(),
                        )
                    }

                Then("로그인 사용자 없음 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_OWNER_NOT_FOUND.message) {
                        upload()
                    }
                }
            }
        }
    })
