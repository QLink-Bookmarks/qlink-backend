package com.qlink.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotMatch

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

                Then("nickname은 랜덤 한글 이름으로 생성한다") {
                    generatedName.nickname shouldContain " "
                    generatedName.nickname shouldNotMatch Regex(".*[0-9a-f]{6}$")
                }

                Then("username은 랜덤 영문 이름과 uuid 파편으로 생성한다") {
                    generatedName.username shouldMatch Regex("[a-z]+[0-9a-f]{6}")
                    generatedName.username shouldNotMatch Regex("[0-9a-f]{32}")
                }
            }
        }
    })
