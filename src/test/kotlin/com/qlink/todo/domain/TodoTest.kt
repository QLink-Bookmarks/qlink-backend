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
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

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

            When("반복 없이 알림 시각이 현재 이전이면") {
                val create = {
                    Todo.create(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = RandomFixture.randomPastInstant(),
                        repeatUntil = null,
                        repeatDays = null,
                        repeatTime = null,
                        repeatTimezone = null,
                        now = Clock.System.now(),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REMINDER_AT_INVALID.message) {
                        create()
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
                        repeatUntil = null,
                        repeatDays = null,
                        repeatTime = null,
                        repeatTimezone = null,
                        now = Clock.System.now(),
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
                        repeatUntil = todo.repeatUntil,
                        repeatDays = todo.repeatDays,
                        repeatTime = todo.repeatTime?.toString(),
                        repeatTimezone = todo.repeatTimezone?.id,
                        now = Clock.System.now(),
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

        Given("반복 알림 계산 테스트") {
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("오늘 반복 시간이 아직 지나지 않았으면") {
                val now = Instant.parse("2026-06-01T00:00:00Z")
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = null,
                        repeatUntil = Instant.parse("2026-06-30T00:00:00Z"),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(10, 30),
                        repeatTimezone = ZoneId.of("Asia/Seoul"),
                    )
                val actual = todo.setNextReminder(now)

                Then("오늘 반복 시간으로 알림을 설정한다") {
                    actual.reminderAt shouldBe Instant.parse("2026-06-01T01:30:00Z")
                }
            }

            When("오늘 반복 시간이 이미 지났으면") {
                val now = Instant.parse("2026-06-01T02:00:00Z")
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = null,
                        repeatUntil = Instant.parse("2026-06-30T00:00:00Z"),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(10, 30),
                        repeatTimezone = ZoneId.of("Asia/Seoul"),
                    )
                val actual = todo.setNextReminder(now)

                Then("다음 주 같은 요일 반복 시간으로 알림을 설정한다") {
                    actual.reminderAt shouldBe Instant.parse("2026-06-08T01:30:00Z")
                }
            }

            When("다음 알림이 반복 종료 시각 이후이면") {
                val now = Instant.parse("2026-06-01T00:00:00Z")
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = null,
                        repeatUntil = Instant.parse("2026-06-01T01:00:00Z"),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(10, 30),
                        repeatTimezone = ZoneId.of("Asia/Seoul"),
                    )
                val actual = todo.setNextReminder(now)

                Then("알림을 비운다") {
                    actual.reminderAt shouldBe null
                }
            }
        }

        Given("반복 설정 검증 테스트") {
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("반복 필드 일부가 누락되면") {
                val create = {
                    Todo(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        repeatUntil = Instant.parse("2026-06-30T00:00:00Z"),
                        repeatDays = listOf(RepeatDay.MON),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REPEAT_FIELDS_INCOMPLETE.message) {
                        create()
                    }
                }
            }

            When("시간대가 올바르지 않으면") {
                val create = {
                    Todo.create(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = null,
                        repeatUntil = Instant.parse("2026-06-30T00:00:00Z"),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = "10:30",
                        repeatTimezone = "Mars/Seoul",
                        now = Clock.System.now(),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REPEAT_TIMEZONE_INVALID.message) {
                        create()
                    }
                }
            }

            When("반복 요일이 비어 있으면") {
                val create = {
                    Todo.create(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = null,
                        repeatUntil = RandomFixture.randomFutureInstantAfterTomorrow(),
                        repeatDays = emptyList(),
                        repeatTime = "10:30",
                        repeatTimezone = null,
                        now = Clock.System.now(),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REPEAT_DAYS_EMPTY.message) {
                        create()
                    }
                }
            }

            When("반복 시간이 HH:mm 형식이 아니면") {
                val create = {
                    Todo.create(
                        linkId = linkId,
                        ownerId = ownerId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        reminderAt = null,
                        repeatUntil = RandomFixture.randomFutureInstantAfterTomorrow(),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = "9:30",
                        repeatTimezone = null,
                        now = Clock.System.now(),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_REPEAT_TIME_INVALID.message) {
                        create()
                    }
                }
            }
        }

        Given("미완료 액션의 상태") {
            val now = Clock.System.now()
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("알림이 없으면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = null)

                Then("ACTIVE") {
                    todo.status(now) shouldBe TodoStatus.ACTIVE
                }
            }

            When("알림 시각이 기준 시각 직전이면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now - 1.nanoseconds)

                Then("OVERDUE") {
                    todo.status(now) shouldBe TodoStatus.OVERDUE
                }
            }

            When("알림 시각이 기준 시각과 같으면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now)

                Then("ACTIVE") {
                    todo.status(now) shouldBe TodoStatus.ACTIVE
                }
            }

            When("알림 시각이 기준 시각 이후이면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now + 1.nanoseconds)

                Then("ACTIVE") {
                    todo.status(now) shouldBe TodoStatus.ACTIVE
                }
            }
        }

        Given("완료된 액션의 상태") {
            val now = Clock.System.now()

            When("알림 시각이 기준 시각보다 지났어도") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = RandomFixture.randomId(),
                        ownerId = RandomFixture.randomId(),
                        reminderAt = RandomFixture.randomPastInstant(),
                        completedAt = RandomFixture.randomPastInstant(),
                    )

                Then("COMPLETED") {
                    todo.status(now) shouldBe TodoStatus.COMPLETED
                }
            }
        }

        Given("미완료 반복 액션의 상태") {
            val now = Clock.System.now()
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("다음 회차 시각이 기준 시각보다 지났으면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = now - 1.hours,
                        repeatUntil = RandomFixture.randomFutureInstantAfterTomorrow(),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(9, 0),
                    )

                Then("OVERDUE") {
                    todo.status(now) shouldBe TodoStatus.OVERDUE
                }
            }

            When("반복이 끝나 알림 시각이 없으면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = null,
                        repeatUntil = RandomFixture.randomPastInstant(),
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(9, 0),
                    )

                Then("ACTIVE") {
                    todo.status(now) shouldBe TodoStatus.ACTIVE
                }
            }
        }
    })
