package com.qlink.linkcontent.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ContentPriorityTest :
    BehaviorSpec({
        Given("저장된 우선순위 값") {
            When("0이면") {
                val priority = ContentPriority.from(0)

                Then("INTERACTIVE다") {
                    priority shouldBe ContentPriority.INTERACTIVE
                }
            }

            When("1이면") {
                val priority = ContentPriority.from(1)

                Then("BULK다") {
                    priority shouldBe ContentPriority.BULK
                }
            }
        }
    })
