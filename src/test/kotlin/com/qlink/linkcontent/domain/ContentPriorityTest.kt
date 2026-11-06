package com.qlink.linkcontent.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
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

            When("알 수 없는 값이면") {
                val parse = { ContentPriority.from(RandomFixture.randomInt(2, 100)) }

                Then("지원하지 않는 우선순위 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_CONTENT_PRIORITY_NOT_SUPPORTED.message) { parse() }
                }
            }
        }
    })
