package com.qlink.link.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class SourceTypeTest :
    BehaviorSpec({
        Given("생성 테스트") {
            val expected = SourceType.entries[Random.nextInt(SourceType.entries.size)]

            When("Source Type 생성을") {
                val actual = SourceType.valueOf(expected.name)

                Then("성공한다") {
                    actual.name shouldBe expected.name
                }
            }
        }
    })
