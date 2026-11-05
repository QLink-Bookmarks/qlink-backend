package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class ImagePurposeTest :
    BehaviorSpec({
        Given("이미지 목적을 해석할 때") {
            When("지원하는 값을 대소문자를 바꿔 전달하면") {
                val parsed = ImagePurpose.entries.associateWith { ImagePurpose.from(RandomFixture.randomCase(it.name)) }

                Then("해당 목적을 반환한다") {
                    parsed.forEach { (expected, actual) -> actual shouldBe expected }
                }
            }

            When("알 수 없는 값을 전달하면") {
                val parse = { ImagePurpose.from("UNKNOWN_${RandomFixture.randomId()}") }

                Then("지원하지 않는 목적 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_PURPOSE_NOT_SUPPORTED.message) {
                        parse()
                    }
                }
            }
        }

        Given("파일 목적의 객체 키를 만들 때") {
            val kinds =
                mapOf(
                    ImagePurpose.PROFILE to "profile",
                    ImagePurpose.POST to "post",
                    ImagePurpose.LINK_PREVIEW to "preview",
                )

            When("파비콘의 직접 업로드 키를 만들면") {
                val create = {
                    ImagePurpose.FAVICON.newObjectKey(environment = "dev", type = ImageType.entries.random())
                }

                Then("ErrorCode.IMAGE_UPLOAD_PURPOSE_UNSUPPORTED로 거부한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_PURPOSE_UNSUPPORTED.message) { create() }
                }
            }

            When("dev·prod에서 세 파일 목적의 키를 만들면") {
                val type = ImageType.entries.random()
                val keys =
                    listOf("dev", "prod").flatMap { environment ->
                        kinds.map { (purpose, kind) ->
                            Triple(environment, kind, purpose.newObjectKey(environment = environment, type = type))
                        }
                    }

                Then("환경·목적 접두어와 실제 형식의 확장자를 가진 루트 키를 반환한다") {
                    keys.forEach { (environment, kind, key) ->
                        key shouldMatch Regex("^$environment-$kind-[0-9a-f]{32}\\.${type.extension}$")
                    }
                }
            }
        }
    })
