package com.qlink.image.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private val JPEG = bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)
private val PNG = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
private val GIF = bytesOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
private val WEBP =
    bytesOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)

class ImageFileTest :
    BehaviorSpec({
        Given("이미지 형식 감지") {
            When("매직 바이트로 형식을 판별하면") {
                Then("각 형식을 매직 바이트로 식별한다") {
                    ImageType.detect(JPEG) shouldBe ImageType.JPEG
                    ImageType.detect(PNG) shouldBe ImageType.PNG
                    ImageType.detect(GIF) shouldBe ImageType.GIF
                    ImageType.detect(WEBP) shouldBe ImageType.WEBP
                }
            }

            When("이미지가 아닌 바이트면") {
                Then("null을 반환한다") {
                    ImageType.detect("not an image".toByteArray()).shouldBeNull()
                    ImageType.detect(bytesOf(0x52, 0x49, 0x46, 0x46)).shouldBeNull()
                }
            }
        }

        Given("ImageFile 생성") {
            When("유효한 이미지면") {
                Then("형식이 매핑된 ImageFile을 생성한다") {
                    val image = ImageFile.of(PNG)
                    image.type shouldBe ImageType.PNG
                    image.newObjectKey().startsWith("images/") shouldBe true
                    image.newObjectKey().endsWith(".png") shouldBe true
                }
            }

            When("빈 파일이면") {
                Then("IMAGE_FILE_REQUIRED 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_REQUIRED.message) {
                        ImageFile.of(ByteArray(0))
                    }
                }
            }

            When("크기 제한을 초과하면") {
                Then("IMAGE_FILE_TOO_LARGE 예외를 반환한다") {
                    val tooLarge = ByteArray(ImageFile.MAX_SIZE_BYTES + 1)
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_TOO_LARGE.message) {
                        ImageFile.of(tooLarge)
                    }
                }
            }

            When("이미지 형식이 아니면") {
                Then("IMAGE_INVALID_FORMAT 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_INVALID_FORMAT.message) {
                        ImageFile.of("not an image".toByteArray())
                    }
                }
            }
        }
    })
