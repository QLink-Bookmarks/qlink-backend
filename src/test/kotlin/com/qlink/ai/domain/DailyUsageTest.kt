package com.qlink.ai.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DailyUsageTest :
    BehaviorSpec({
        Given("일별 AI 사용량 테스트") {
            When("사용량을 추가하면") {
                val usage =
                    DailyUsage(
                        userProviderId = 1L,
                        modelId = 1L,
                        usageDate = LocalDate.now(),
                        requests = 1,
                        tokens = 10,
                    )
                val actual = usage.addUsage(tokens = 5)

                Then("요청 수와 토큰 수를 누적한다") {
                    actual.requests shouldBe 2
                    actual.tokens shouldBe 15
                }
            }

            When("요청 제한을 초과하면") {
                val usage =
                    DailyUsage(
                        userProviderId = 1L,
                        modelId = 1L,
                        usageDate = LocalDate.now(),
                        requests = 2,
                        tokens = 10,
                    )
                val model =
                    AvailableModel(
                        providerId = 1L,
                        model = "test-model",
                        priority = 1,
                        rpdLimit = 2,
                        tpdLimit = 100,
                    )

                Then("제한 초과로 판단한다") {
                    usage.isOverLimit(model) shouldBe true
                }
            }

            When("토큰 제한을 초과하지 않으면") {
                val usage =
                    DailyUsage(
                        userProviderId = 1L,
                        modelId = 1L,
                        usageDate = LocalDate.now(),
                        requests = 1,
                        tokens = 10,
                    )
                val model =
                    AvailableModel(
                        providerId = 1L,
                        model = "test-model",
                        priority = 1,
                        rpdLimit = null,
                        tpdLimit = 100,
                    )

                Then("제한 초과가 아니다") {
                    usage.isOverLimit(model) shouldBe false
                }
            }
        }
    })
