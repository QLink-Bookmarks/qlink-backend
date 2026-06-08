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
                val actual = Notification.todo(todo)

                Then("성공한다") {
                    actual shouldNotBe null
                    actual!!.userId shouldBe todo.ownerId
                    actual.title shouldBe todo.title
                    actual.context shouldBe NotificationContext.TODO
                    actual.contextId shouldBe todo.id
                    actual.willFireAt shouldBe reminderAt
                    actual.isPending shouldBe true
                }
            }

            When("알림 없는 할 일로 notification 생성을") {
                val actual = Notification.todo(Todo(linkId = todo.linkId, ownerId = todo.ownerId, title = todo.title))

                Then("생성하지 않는다") {
                    actual shouldBe null
                }
            }

            When("완료된 할 일로 notification 생성을") {
                val actual = Notification.todo(todo.complete(Clock.System.now()))

                Then("생성하지 않는다") {
                    actual shouldBe null
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
                val notification =
                    Notification(
                        userId = RandomFixture.randomId(),
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                        firedAt = firedAt,
                    )

                Then("읽음 시간이 반영된 알림을 반환한다") {
                    notification.markRead(readAt).readAt shouldBe readAt
                }
            }

            When("이미 읽은 알림이면") {
                val firedAt = Clock.System.now()
                val readAt = Clock.System.now()
                val notification =
                    Notification(
                        userId = RandomFixture.randomId(),
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                        firedAt = firedAt,
                        readAt = readAt,
                    )

                Then("기존 알림을 그대로 반환한다") {
                    notification.markRead(Clock.System.now()) shouldBe notification
                }
            }

            When("아직 발송되지 않은 알림이면") {
                val notification =
                    Notification(
                        userId = RandomFixture.randomId(),
                        title = RandomFixture.randomSentenceWithMax(50),
                        message = RandomFixture.randomSentenceWithMax(200),
                        context = NotificationContext.TODO,
                        contextId = RandomFixture.randomId(),
                        willFireAt = Clock.System.now(),
                    )

                Then("비즈니스 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.NOTIFICATION_NOT_FIRED.message) {
                        notification.markRead(Clock.System.now())
                    }
                }
            }
        }
    })
