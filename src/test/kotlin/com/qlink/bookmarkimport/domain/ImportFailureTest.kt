package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ImportFailureTest :
    BehaviorSpec({
        Given("가져오기 계획의 실패") {
            val importId = RandomFixture.randomId()
            val failure = ImportFixture.createPlanFailureOf()

            When("실패 행을 만들면") {
                val row = ImportFailure.create(importId = importId, failure = failure)

                Then("가져오기 id와 실패한 링크의 제목·URL·사유가 담기고 id는 null") {
                    row.id shouldBe null
                    row.importId shouldBe importId
                    row.title shouldBe failure.title
                    row.url shouldBe failure.url
                    row.reason shouldBe failure.reason
                }
            }
        }
    })
