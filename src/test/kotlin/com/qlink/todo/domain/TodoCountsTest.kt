package com.qlink.todo.domain

import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds

class TodoCountsTest :
    BehaviorSpec({
        Given("미완료 할 일의 알림 시각") {
            val now = Clock.System.now()
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("기준 시각 직전이면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now - 1.nanoseconds)

                Then("기한이 지났다") {
                    todo.isOverdue(now) shouldBe true
                    todo.isDueSoon(now) shouldBe false
                }
            }

            When("기준 시각과 같으면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now)

                Then("임박했고 기한이 지나지 않았다") {
                    todo.isOverdue(now) shouldBe false
                    todo.isDueSoon(now) shouldBe true
                }
            }

            When("정확히 24시간 뒤이면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now + 24.hours)

                Then("임박하지 않았다") {
                    todo.isDueSoon(now) shouldBe false
                }
            }

            When("알림 시각이 없으면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = null)

                Then("어느 시간 범주에도 들지 않는다") {
                    todo.isOverdue(now) shouldBe false
                    todo.isDueSoon(now) shouldBe false
                }
            }

            When("반복 할 일의 다음 알림이 저장돼 있으면") {
                val todo =
                    TodoFixture.createRandomTodoOf(
                        linkId = linkId,
                        ownerId = ownerId,
                        reminderAt = now + 1.hours,
                        repeatUntil = now + 48.hours,
                        repeatDays = listOf(RepeatDay.MON),
                        repeatTime = LocalTime.of(9, 0),
                        repeatTimezone = ZoneId.of("Asia/Seoul"),
                    )

                Then("저장된 다음 알림으로 판단한다") {
                    todo.isDueSoon(now) shouldBe true
                }
            }
        }

        Given("완료된 할 일") {
            val now = Clock.System.now()
            val linkId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()

            When("알림이 과거이면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now - 1.hours, completedAt = now)

                Then("기한이 지난 미완료 건으로 세지 않는다") {
                    todo.isOverdue(now) shouldBe false
                }
            }

            When("알림이 임박하면") {
                val todo = TodoFixture.createRandomTodoOf(linkId = linkId, ownerId = ownerId, reminderAt = now + 1.hours, completedAt = now)

                Then("임박한 미완료 건으로 세지 않는다") {
                    todo.isDueSoon(now) shouldBe false
                }
            }
        }
    })
