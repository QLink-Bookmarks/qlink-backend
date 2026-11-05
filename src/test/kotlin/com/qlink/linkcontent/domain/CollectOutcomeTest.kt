package com.qlink.linkcontent.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

class CollectOutcomeTest :
    BehaviorSpec({
        Given("수집 결과 메트릭 라벨") {
            When("라벨 집합을 읽으면") {
                val labels = CollectOutcome.entries.map { it.name }

                Then("정해진 10개뿐이다(메트릭 라벨 수 고정)") {
                    labels shouldContainExactly
                        listOf(
                            "COLLECTED",
                            "FAILED",
                            "BLOCKED",
                            "RETRY_SCHEDULED",
                            "RELEASED_THROTTLED",
                            "RELEASED_ERROR",
                            "GIVE_UP",
                            "SKIPPED",
                            "LINK_GONE",
                            "URL_CHANGED",
                        )
                }
            }
        }
    })
