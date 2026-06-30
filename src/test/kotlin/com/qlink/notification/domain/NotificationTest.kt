package com.qlink.notification.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import com.qlink.todo.domain.Todo
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class NotificationTest :
    BehaviorSpec({
        Given("할 일 알림 생성 테스트") {
            val reminderAt = Clock.System.now().plus(1.days)
            val todo =
                Todo(
                    id = RandomFixture.randomId(),
                    linkId = RandomFixture.randomId(),
                    ownerId = RandomFixture.randomId(),
                    title = RandomFixture.randomSentenceWithMax(50),
                    reminderAt = reminderAt,
                )

            When("알림 있는 할 일로 notification 생성을") {
                val actual = Notification.todo(todo = todo, linkTitle = "내 북마크", linkUrl = "https://example.com/a")

                Then("성공한다") {
                    actual shouldNotBe null
                    actual!!.userId shouldBe todo.ownerId
                    actual.title shouldBe todo.title
                    actual.message shouldBe "북마크 링크: 내 북마크 (https://example.com/a)"
                    actual.context shouldBe NotificationContext.TODO
                    actual.contextId shouldBe todo.id
                    actual.willFireAt shouldBe reminderAt
                    actual.isPending shouldBe true
                }
            }

            When("링크 제목과 url이 길면") {
                val longTitle = "가".repeat(40)
                val longUrl = "https://example.com/${"path/".repeat(20)}"
                val actual = Notification.todo(todo = todo, linkTitle = longTitle, linkUrl = longUrl)

                Then("제목은 20자, url은 30자로 잘리고 …가 붙는다") {
                    actual shouldNotBe null
                    actual!!.message shouldBe "북마크 링크: ${"가".repeat(19)}… (${longUrl.take(29)}…)"
                }
            }

            When("알림 없는 할 일로 notification 생성을") {
                val actual =
                    Notification.todo(
                        todo = Todo(linkId = todo.linkId, ownerId = todo.ownerId, title = todo.title),
                        linkTitle = "내 북마크",
                        linkUrl = "https://example.com/a",
                    )

                Then("생성하지 않는다") {
                    actual shouldBe null
                }
            }

            When("완료된 할 일로 notification 생성을") {
                val actual =
                    Notification.todo(
                        todo = todo.complete(Clock.System.now()),
                        linkTitle = "내 북마크",
                        linkUrl = "https://example.com/a",
                    )

                Then("생성하지 않는다") {
                    actual shouldBe null
                }
            }
        }

        Given("할 일 기원(isFrom) 판별 테스트") {
            val reminderAt = Clock.System.now().plus(1.days)
            val todo =
                Todo(
                    id = RandomFixture.randomId(),
                    linkId = RandomFixture.randomId(),
                    ownerId = RandomFixture.randomId(),
                    title = RandomFixture.randomSentenceWithMax(50),
                    reminderAt = reminderAt,
                )
            val notification = Notification.todo(todo = todo, linkTitle = "내 북마크", linkUrl = "https://example.com/a")!!

            When("같은 할 일로 판별하면") {
                Then("true 를 반환한다") {
                    notification.isFrom(todo) shouldBe true
                }
            }

            When("이미 발송된(fired) 알림이어도") {
                val fired = notification.markFired(Clock.System.now())

                Then("상태와 무관하게 true 를 반환한다") {
                    fired.isPending shouldBe false
                    fired.isFrom(todo) shouldBe true
                }
            }

            When("리마인더 시각이 다른 할 일로 판별하면") {
                val rescheduled =
                    Todo(
                        id = todo.id,
                        linkId = todo.linkId,
                        ownerId = todo.ownerId,
                        title = todo.title,
                        reminderAt = reminderAt.plus(1.days),
                    )

                Then("false 를 반환한다") {
                    notification.isFrom(rescheduled) shouldBe false
                }
            }

            When("다른 할 일(contextId)로 판별하면") {
                val otherTodo =
                    Todo(
                        id = todo.id!! + 1,
                        linkId = todo.linkId,
                        ownerId = todo.ownerId,
                        title = todo.title,
                        reminderAt = reminderAt,
                    )

                Then("false 를 반환한다") {
                    notification.isFrom(otherTodo) shouldBe false
                }
            }
        }

        Given("알림 상태 변경 테스트") {
            val notification =
                Notification(
                    userId = RandomFixture.randomId(),
                    title = RandomFixture.randomSentenceWithMax(50),
                    message = RandomFixture.randomSentenceWithMax(200),
                    context = NotificationContext.TODO,
                    contextId = RandomFixture.randomId(),
                    willFireAt = Clock.System.now().plus(1.days),
                )

            When("스케줄 등록 시간을 표시하면") {
                val scheduledAt = Clock.System.now()
                val actual = notification.markScheduled(scheduledAt)

                Then("pending 상태를 유지한다") {
                    actual.scheduledAt shouldBe scheduledAt
                    actual.isPending shouldBe true
                }
            }

            When("발송 성공 처리하면") {
                val firedAt = Clock.System.now()
                val actual = notification.markFired(firedAt)

                Then("성공 카운트와 발송 시간을 저장한다") {
                    actual.firedAt shouldBe firedAt
                    actual.successCount shouldBe 1
                    actual.failureCount shouldBe 0
                    actual.isPending shouldBe false
                }
            }

            When("발송 실패 처리하면") {
                val failedAt = Clock.System.now()
                val actual = notification.markFailed(failedAt)

                Then("실패 카운트와 실패 시간을 저장한다") {
                    actual.failedAt shouldBe failedAt
                    actual.successCount shouldBe 0
                    actual.failureCount shouldBe 1
                    actual.isPending shouldBe false
                }
            }

            When("일부 실패한 발송 결과를 기록하면") {
                val handledAt = Clock.System.now()
                val actual =
                    notification.recordSendResult(
                        handledAt = handledAt,
                        successCount = 2,
                        failureCount = 1,
                    )

                Then("발송 시간과 실패 시간을 모두 저장한다") {
                    actual.firedAt shouldBe handledAt
                    actual.failedAt shouldBe handledAt
                    actual.successCount shouldBe 2
                    actual.failureCount shouldBe 1
                    actual.isPending shouldBe false
                }
            }

            When("성공 없는 발송 결과를 기록하면") {
                val handledAt = Clock.System.now()
                val actual =
                    notification.recordSendResult(
                        handledAt = handledAt,
                        successCount = 0,
                        failureCount = 2,
                    )

                Then("실패 시간과 결과 건수를 저장한다") {
                    actual.firedAt shouldBe null
                    actual.failedAt shouldBe handledAt
                    actual.successCount shouldBe 0
                    actual.failureCount shouldBe 2
                    actual.isPending shouldBe false
                }
            }
        }

        Given("알림 검증 테스트") {
            When("제목이 비어 있으면") {
                val create = {
                    Notification(
                        userId = RandomFixture.randomId(),
                        title = " ",
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now().plus(1.days),
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.NOTIFICATION_TITLE_BLANK.message) {
                        create()
                    }
                }
            }
        }

        Given("알림 읽음 처리 테스트") {
            When("읽음 시간을 기록하면") {
                val firedAt = Clock.System.now()
                val readAt = Clock.System.now()
                val userId = RandomFixture.randomId()
                val notification =
                    Notification(
                        userId = userId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                        firedAt = firedAt,
                    )

                Then("읽음 시간이 반영된 알림을 반환한다") {
                    notification.markRead(loginId = userId, readAt = readAt).readAt shouldBe readAt
                }
            }

            When("이미 읽은 알림이면") {
                val firedAt = Clock.System.now()
                val readAt = Clock.System.now()
                val userId = RandomFixture.randomId()
                val notification =
                    Notification(
                        userId = userId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                        firedAt = firedAt,
                        readAt = readAt,
                    )

                Then("기존 알림을 그대로 반환한다") {
                    notification.markRead(loginId = userId, readAt = Clock.System.now()) shouldBe notification
                }
            }

            When("로그인 사용자와 알림 사용자가 다르면") {
                val userId = RandomFixture.randomId()
                val notification =
                    Notification(
                        userId = userId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                        firedAt = Clock.System.now(),
                    )

                Then("기존 알림을 그대로 반환한다") {
                    notification.markRead(loginId = userId + 1, readAt = Clock.System.now()) shouldBe notification
                }
            }

            When("아직 발송되지 않은 알림이면") {
                val userId = RandomFixture.randomId()
                val notification =
                    Notification(
                        userId = userId,
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                    )

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.NOTIFICATION_NOT_FIRED.message) {
                        notification.markRead(loginId = userId, readAt = Clock.System.now())
                    }
                }
            }
        }
    })
