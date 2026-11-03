package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.NotificationFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ScheduleTodoNotificationServiceTest :
    BaseServiceTest({
        val scheduleTodoNotificationService = koinGet<ScheduleTodoNotificationService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val notificationRepository = koinGet<NotificationRepository>()

        suspend fun notificationsOf(todo: Todo): List<Notification> =
            notificationRepository.findByContext(
                context = NotificationContext.TODO,
                contextId = todo.id!!,
            )

        Given("알림 시간이 있는 미완료 할 일") {
            lateinit var user: User
            lateinit var link: Link
            lateinit var upcomingTodo: Todo
            lateinit var overdueTodo: Todo

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                upcomingTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            reminderAt = RandomFixture.randomFutureInstantAfterTomorrow(),
                        ),
                    )
                overdueTodo =
                    todoRepository.insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = link.id!!,
                            ownerId = user.id!!,
                            reminderAt = RandomFixture.randomPastInstant(),
                        ),
                    )
            }

            When("할 일의 알림이 하나도 없으면") {
                val replace =
                    suspend {
                        scheduleTodoNotificationService.replaceForTodo(upcomingTodo)
                    }

                Then("알림 시간에 발송될 대기 알림 1건이 저장된다") {
                    shouldNotThrow<BusinessException> {
                        replace()
                    }

                    val actual = notificationsOf(upcomingTodo).single()
                    actual.isPending shouldBe true
                    actual.userId shouldBe user.id
                    actual.willFireAt shouldBe upcomingTodo.reminderAt
                }
            }

            When("같은 일정의 대기 알림이 있으면") {
                val replace =
                    suspend {
                        scheduleTodoNotificationService.replaceForTodo(upcomingTodo)
                    }

                Then("기존 대기 알림은 지워지고 같은 일정의 대기 알림 1건만 남는다") {
                    val pending = notificationRepository.insert(NotificationFixture.createTodoNotificationOf(todo = upcomingTodo))

                    shouldNotThrow<BusinessException> {
                        replace()
                    }

                    val actual = notificationsOf(upcomingTodo).single()
                    actual.id shouldNotBe pending.id
                    actual.isPending shouldBe true
                    actual.willFireAt shouldBe upcomingTodo.reminderAt
                }
            }

            When("같은 일정의 알림이 이미 발송(fired)됐으면") {
                val replace =
                    suspend {
                        scheduleTodoNotificationService.replaceForTodo(overdueTodo)
                    }

                Then("새 알림 없이 발송된 알림만 그대로 남는다") {
                    val fired =
                        notificationRepository.insert(
                            NotificationFixture.createTodoNotificationOf(
                                todo = overdueTodo,
                                firedAt = RandomFixture.randomPastInstant(),
                            ),
                        )

                    shouldNotThrow<BusinessException> {
                        replace()
                    }

                    val actual = notificationsOf(overdueTodo).single()
                    actual.id shouldBe fired.id
                    actual.firedAt shouldBe fired.firedAt
                }
            }

            When("같은 일정의 알림이 이미 실패(failed)했으면") {
                val replace =
                    suspend {
                        scheduleTodoNotificationService.replaceForTodo(overdueTodo)
                    }

                Then("새 알림 없이 실패한 알림만 그대로 남는다") {
                    val failed =
                        notificationRepository.insert(
                            NotificationFixture.createTodoNotificationOf(
                                todo = overdueTodo,
                                failedAt = RandomFixture.randomPastInstant(),
                            ),
                        )

                    shouldNotThrow<BusinessException> {
                        replace()
                    }

                    val actual = notificationsOf(overdueTodo).single()
                    actual.id shouldBe failed.id
                    actual.failedAt shouldBe failed.failedAt
                }
            }

            When("다른 일정의 발송된 알림만 있으면") {
                val replace =
                    suspend {
                        scheduleTodoNotificationService.replaceForTodo(upcomingTodo)
                    }

                Then("발송된 알림은 남고 현재 알림 시간의 대기 알림 1건이 추가된다") {
                    val fired =
                        notificationRepository.insert(
                            NotificationFixture.createTodoNotificationOf(
                                todo = upcomingTodo,
                                willFireAt = RandomFixture.randomPastInstant(),
                                firedAt = RandomFixture.randomPastInstant(),
                            ),
                        )

                    shouldNotThrow<BusinessException> {
                        replace()
                    }

                    val actual = notificationsOf(upcomingTodo)
                    actual.map { it.willFireAt } shouldContainExactlyInAnyOrder listOf(fired.willFireAt, upcomingTodo.reminderAt)
                    actual.single { it.isPending }.willFireAt shouldBe upcomingTodo.reminderAt
                }
            }
        }
    })
