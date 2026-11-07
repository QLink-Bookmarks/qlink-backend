package com.qlink.todo.domain

import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TodoStatusTest :
    BehaviorSpec({
        Given("상태 필터 값을 해석할 때") {
            When("active·overdue·completed를 대소문자를 섞어 보내면") {
                val actual = listOf("active", "overdue", "completed").map { TodoStatus.from(RandomFixture.randomCase(it)) }

                Then("해당 상태다") {
                    actual shouldBe listOf(TodoStatus.ACTIVE, TodoStatus.OVERDUE, TodoStatus.COMPLETED)
                }
            }

            When("모르는 값이면") {
                val actual = TodoStatus.from("upcoming")

                Then("null이다") {
                    actual shouldBe null
                }
            }
        }
    })
