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

            When("살아 있는 링크의 snapshot으로 처음 예약을 만들면") {
                val create = suspend { scheduleTodoNotificationService.createForTodo(upcomingTodo) }
                Then("해당 액션, 수신자, 예정 시각의 pending을 저장한다") {
                    shouldNotThrow<BusinessException> { create() }
                    val actual = notificationsOf(upcomingTodo).single()
                    actual.contextId shouldBe upcomingTodo.id
                    actual.userId shouldBe upcomingTodo.ownerId
                    actual.willFireAt shouldBe upcomingTodo.reminderAt
                    actual.isPending shouldBe true
                }
            }

            When("예정 시각을 교체하면") {
                lateinit var pending: Notification
                lateinit var fired: Notification
                lateinit var failed: Notification
                lateinit var changedTodo: Todo
                beforeTest {
                    pending = notificationRepository.insert(NotificationFixture.createTodoNotificationOf(todo = upcomingTodo))
                    fired =
                        notificationRepository.insert(
                            NotificationFixture.createTodoNotificationOf(
                                todo = upcomingTodo,
                                willFireAt = RandomFixture.randomPastInstant(),
                                firedAt = RandomFixture.randomPastInstant(),
                            ),
                        )
                    failed =
                        notificationRepository.insert(
                            NotificationFixture.createTodoNotificationOf(
                                todo = upcomingTodo,
                                willFireAt = RandomFixture.randomPastInstant(),
                                failedAt = RandomFixture.randomPastInstant(),
                            ),
                        )
                    changedTodo =
                        todoRepository.update(
                            TodoFixture.createRescheduledTodoOf(
                                todo = upcomingTodo,
                                reminderAt = RandomFixture.randomFutureInstantAfterTomorrow(),
                            ),
                        )
                }
                val replace = suspend { scheduleTodoNotificationService.replaceForTodo(changedTodo) }
                Then("기존 pending만 지우고 이력과 새로운 시각의 pending을 보존한다") {
                    shouldNotThrow<BusinessException> { replace() }
                    notificationRepository.findById(pending.id!!) shouldBe null
                    notificationsOf(changedTodo).single { it.isPending }.willFireAt shouldBe changedTodo.reminderAt
                    notificationRepository.findById(fired.id!!)!!.firedAt shouldBe fired.firedAt
                    notificationRepository.findById(failed.id!!)!!.failedAt shouldBe failed.failedAt
                }
            }

            When("링크와 액션을 먼저 삭제하고 예전 snapshot으로 생성하면") {
                beforeTest { linkRepository.deleteById(link.id!!) }
                val create = suspend { scheduleTodoNotificationService.createForTodo(upcomingTodo) }
                Then("pending을 다시 만들지 않는다") {
                    shouldNotThrow<BusinessException> { create() }
                    notificationsOf(upcomingTodo) shouldBe emptyList()
                    linkRepository.findById(link.id!!) shouldBe null
                    todoRepository.findById(upcomingTodo.id!!) shouldBe null
                }
            }

            When("링크와 pending을 먼저 지우고 예전 snapshot으로 교체하면") {
                lateinit var unrelated: Notification
                beforeTest {
                    unrelated = notificationRepository.insert(NotificationFixture.createRandomNotificationOf(user.id!!))
                    notificationRepository.insert(NotificationFixture.createTodoNotificationOf(todo = upcomingTodo))
                    notificationRepository.deletePendingByContext(context = NotificationContext.TODO, contextId = upcomingTodo.id!!)
                    linkRepository.deleteById(link.id!!)
                }
                val replace = suspend { scheduleTodoNotificationService.replaceForTodo(upcomingTodo) }
                Then("pending을 재생성하지 않고 무관한 알림은 보존한다") {
                    shouldNotThrow<BusinessException> { replace() }
                    notificationsOf(upcomingTodo) shouldBe emptyList()
                    notificationRepository.findById(unrelated.id!!)!!.willFireAt shouldBe unrelated.willFireAt
                }
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
