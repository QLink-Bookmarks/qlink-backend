package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TodoTest :
    BehaviorSpec({
        Given("생성 테스트") {
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()
            val title = RandomFixture.randomSentenceWithMax(50)

            When("할 일 생성을") {
                val actual =
                    Todo(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = title,
                    )

                Then("성공한다") {
                    actual shouldNotBe null
                    actual.linkId shouldBe linkId
                    actual.ownerId shouldBe ownerId
                    actual.title shouldBe title
                    actual.completedAt shouldBe null
                    actual.isCompleted shouldBe false
                }
            }

            When("제목이 공백이면") {
                val emptyCreate = {
                    Todo(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = "",
                    )
                }
                val blankCreate = {
                    Todo(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = " ",
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) {
                        emptyCreate()
                    }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) {
                        blankCreate()
                    }
                }
            }

            When("제목이 최대 50자를 넘으면") {
                val overMaxCreate = {
                    Todo(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentence(51, 100),
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_OVER_MAX.message) {
                        overMaxCreate()
                    }
                }
            }
        }
    })
