package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FakeLinkPreviewSyncClient
import com.qlink.support.fixture.LinkPreviewFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class CreateLinkPreviewServiceTest :
    BaseServiceTest({
        val service = koinGet<CreateLinkPreviewService>()
        val userRepository = koinGet<UserRepository>()
        val client = koinGet<FakeLinkPreviewSyncClient>()

        Given("존재하는 로그인 사용자와 유효한 URL") {
            lateinit var user: User
            lateinit var url: String
            beforeTest {
                client.reset()
                user = userRepository.insert(UserFixture.createRandomValidUser())
                url = RandomFixture.randomUrl()
            }

            When("대표 이미지 키가 반환되면") {
                val key = LinkPreviewFixture.key(kind = "preview")
                val create =
                    suspend {
                        client.result = LinkPreviewResult(title = RandomFixture.randomSentenceWithMax(300), thumbnailKey = key)
                        service.createPreview(user.id!!, url)
                    }

                Then("현재 환경의 CDN URL만 반환하고 사용자는 유지된다") {
                    val response = create()
                    response.url shouldBe url
                    response.thumbnailUrl shouldBe "https://images.archivelink.app/$key"
                    response.faviconUrl shouldBe null
                    client.calls.single() shouldBe (url to LinkPreviewMode.PREVIEW)
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("파비콘 키가 반환되면") {
                val key = LinkPreviewFixture.key(kind = "favicon")
                val create =
                    suspend {
                        client.result = LinkPreviewResult(faviconKey = key)
                        service.createPreview(user.id!!, url)
                    }

                Then("파비콘 URL만 반환한다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe "https://images.archivelink.app/$key"
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("이미지가 없으면") {
                val create =
                    suspend {
                        client.result = LinkPreviewResult()
                        service.createPreview(user.id!!, url)
                    }

                Then("빈 이미지 응답을 반환한다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe null
                    response.isVideo shouldBe false
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("Lambda 호출에 실패하면") {
                val create =
                    suspend {
                        client.failure = IllegalStateException("fixture failure")
                        service.createPreview(user.id!!, url)
                    }

                Then("빈 프리뷰를 반환한다") {
                    val response = create()
                    response.title shouldBe null
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe null
                    response.isVideo shouldBe false
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("다른 환경의 키가 반환되면") {
                val create =
                    suspend {
                        client.result = LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview", environment = "prod"))
                        service.createPreview(user.id!!, url)
                    }

                Then("공개 URL로 조립하지 않는다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("허용 형식 밖의 키가 반환되면") {
                val create =
                    suspend {
                        client.result = LinkPreviewResult(thumbnailKey = "dev-preview-not-a-hash.png", faviconKey = "../private.png")
                        service.createPreview(user.id!!, url)
                    }

                Then("어느 키도 공개 URL로 조립하지 않는다") {
                    val response = create()
                    response.thumbnailUrl shouldBe null
                    response.faviconUrl shouldBe null
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }

            When("동일 URL을 반복 요청하면") {
                val create = suspend { service.createPreview(user.id!!, url) }

                Then("원본 URL을 유지하고 사용자 DB 상태도 유지된다") {
                    val first = create()
                    val second = create()
                    first.url shouldBe url
                    second.url shouldBe url
                    client.calls.size shouldBe 2
                    userRepository.findById(user.id!!)?.id shouldBe user.id
                }
            }
        }

        Given("유효하지 않은 URL") {
            lateinit var user: User
            beforeTest {
                client.reset()
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("절대 HTTP(S) URL이 아니면") {
                val create = suspend { service.createPreview(user.id!!, "file:///etc/passwd") }

                Then("LINK_PREVIEW_URL_INVALID를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_PREVIEW_URL_INVALID.message) { create() }
                    client.calls.size shouldBe 0
                }
            }
        }

        Given("존재하지 않는 사용자") {
            beforeTest { client.reset() }

            When("프리뷰를 요청하면") {
                val create = suspend { service.createPreview(RandomFixture.randomId(), RandomFixture.randomUrl()) }

                Then("LINK_PREVIEW_OWNER_NOT_FOUND를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_PREVIEW_OWNER_NOT_FOUND.message) { create() }
                    client.calls.size shouldBe 0
                }
            }
        }
    })
