package com.qlink.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain

class RandomUserNameGeneratorTest :
    BehaviorSpec({
        Given("랜덤 사용자 이름 생성기가 있을 때") {
            val generator = RandomUserNameGenerator()

            When("사용자 이름을 생성하면") {
                val generatedName = generator.generate()

                Then("username과 nickname을 생성한다") {
                    generatedName.username.shouldNotBeBlank()
                    generatedName.username shouldNotContain "-"
                    generatedName.nickname.shouldNotBeBlank()
                }
            }
        }
    })
