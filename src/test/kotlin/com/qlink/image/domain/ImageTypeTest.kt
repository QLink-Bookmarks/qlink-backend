package com.qlink.image.domain

import com.qlink.support.fixture.ImageFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ImageTypeTest :
    BehaviorSpec({
        Given("실제 이미지 바이트") {
            When("JPEG·PNG·GIF·WEBP를 판별하면") {
                val detected = ImageType.entries.associateWith { ImageType.detect(ImageFixture.validImage(it)) }

                Then("각각의 실제 형식을 반환한다") {
                    detected.forEach { (expected, actual) -> actual shouldBe expected }
                }
            }

            When("이미지 헤더는 있지만 디코딩할 수 없으면") {
                val detected = ImageType.entries.map { ImageType.detect(ImageFixture.headerOnlyImage(it)) }

                Then("형식을 반환하지 않는다") {
                    detected.forEach { it.shouldBeNull() }
                }
            }

            When("ICO·SVG를 판별하면") {
                val detected = listOf(ImageFixture.ico(), ImageFixture.svg()).map(ImageType::detect)

                Then("형식을 반환하지 않는다") {
                    detected.forEach { it.shouldBeNull() }
                }
            }
        }
    })
