package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.ImageFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ImageFileTest :
    BehaviorSpec({
        Given("직접 업로드할 파일을 만들 때") {
            When("유효한 이미지를 전달하면") {
                val type = ImageType.entries.random()
                val bytes = ImageFixture.validImage(type)
                val image = ImageFile.of(bytes)

                Then("실제 형식과 바이트를 가진 파일을 반환한다") {
                    image.type shouldBe type
                    image.bytes shouldBe bytes
                }
            }

            When("파일이 비어 있으면") {
                val create = { ImageFile.of(ByteArray(0)) }

                Then("이미지 파일 필요 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_REQUIRED.message) {
                        create()
                    }
                }
            }

            When("크기가 정확히 10MiB이면") {
                val image = ImageFile.of(ImageFixture.validPngOfSize(ImageFile.MAX_SIZE_BYTES))

                Then("파일을 생성한다") {
                    image.type shouldBe ImageType.PNG
                    image.bytes.size shouldBe ImageFile.MAX_SIZE_BYTES
                }
            }

            When("크기가 10MiB를 1바이트 넘으면") {
                val create = { ImageFile.of(ImageFixture.validPngOfSize(ImageFile.MAX_SIZE_BYTES + 1)) }

                Then("파일 크기 초과 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_TOO_LARGE.message) {
                        create()
                    }
                }
            }

            When("ICO·SVG 또는 디코딩할 수 없는 파일을 전달하면") {
                val files =
                    listOf(
                        ImageFixture.ico(),
                        ImageFixture.svg(),
                        ImageFixture.headerOnlyImage(ImageType.entries.random()),
                    )
                val create = { bytes: ByteArray -> ImageFile.of(bytes) }

                Then("이미지 형식 예외를 반환한다") {
                    files.forEach { bytes ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_INVALID_FORMAT.message) {
                            create(bytes)
                        }
                    }
                }
            }

            When("생성 전후에 입력·반환 바이트 배열을 변경하면") {
                val input = ImageFixture.validImage()
                val original = input.copyOf()
                val image = ImageFile.of(input)
                input.fill(0)
                image.bytes.fill(0)

                Then("파일 내부 바이트는 바뀌지 않는다") {
                    image.bytes shouldBe original
                }
            }
        }
    })
