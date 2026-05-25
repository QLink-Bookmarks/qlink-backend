package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock

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

        Given("소유자 검증 테스트") {
            val ownerId = RandomFixture.randomId()
            val todo =
                Todo(
                    linkId = RandomFixture.randomId(),
                    ownerId = ownerId,
                    title = RandomFixture.randomSentenceWithMax(50),
                )

            When("소유자 검증을") {
                val validate = {
                    todo.validateOwner(ownerId)
                }

                Then("성공한다") {
                    shouldNotThrow<BusinessException> {
                        validate()
                    }
                }
            }

            When("다른 소유자로 검증하면") {
                val otherOwnerId = ownerId + 1
                val validate = {
                    todo.validateOwner(otherOwnerId)
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_DIFFERENT_OWNER.message) {
                        validate()
                    }
                }
            }
        }

        Given("수정 테스트") {
            val todo =
                Todo(
                    id = RandomFixture.randomId(),
                    linkId = RandomFixture.randomId(),
                    ownerId = RandomFixture.randomId(),
                    title = RandomFixture.randomSentenceWithMax(50),
                )

            When("할 일 수정을") {
                val linkId = RandomFixture.randomId()
                val title = RandomFixture.randomSentenceWithMax(50)
                val actual =
                    todo.update(
                        linkId = linkId,
                        title = title,
                        reminderAt = null,
                    )

                Then("성공한다") {
                    actual.id shouldBe todo.id
                    actual.linkId shouldBe linkId
                    actual.ownerId shouldBe todo.ownerId
                    actual.title shouldBe title
                    actual.reminderAt shouldBe null
                    actual.completedAt shouldBe todo.completedAt
                }
            }

            When("같은 링크인지 확인하면") {
                val sameLinkId = todo.linkId
                val otherLinkId = todo.linkId + 1

                Then("현재 링크와 다르면 true를 반환한다") {
                    todo.isDifferentLink(sameLinkId) shouldBe false
                    todo.isDifferentLink(otherLinkId) shouldBe true
                }
            }

            When("수정 제목이 공백이면") {
                val update = {
                    todo.update(
                        linkId = todo.linkId,
                        title = "",
                        reminderAt = todo.reminderAt,
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_TITLE_BLANK.message) {
                        update()
                    }
                }
            }
        }

        Given("완료 상태 변경 테스트") {
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("미완료 할 일을 완료하면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        completedAt = null,
                    )
                val completedAt = Clock.System.now()
                val actual = todo.complete(completedAt)

                Then("완료 상태가 된다") {
                    actual.id shouldBe todo.id
                    actual.completedAt shouldBe completedAt
                    actual.isCompleted shouldBe true
                }
            }

            When("완료된 할 일을 다시 완료하면") {
                val originalCompletedAt = Clock.System.now()
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        completedAt = originalCompletedAt,
                    )
                val actual = todo.complete(Clock.System.now())

                Then("기존 완료 시간이 유지된다") {
                    actual.completedAt shouldBe originalCompletedAt
                    actual.isCompleted shouldBe true
                }
            }

            When("완료된 할 일을 미완료하면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        completedAt = Clock.System.now(),
                    )
                val actual = todo.incomplete()

                Then("미완료 상태가 된다") {
                    actual.completedAt shouldBe null
                    actual.isCompleted shouldBe false
                }
            }

            When("미완료 할 일을 다시 미완료하면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        completedAt = null,
                    )
                val actual = todo.incomplete()

                Then("미완료 상태가 유지된다") {
                    actual.completedAt shouldBe null
                    actual.isCompleted shouldBe false
                }
            }
        }
    })
