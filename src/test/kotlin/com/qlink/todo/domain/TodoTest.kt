package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.assertions.throwables.shouldNotThrow
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
    })
