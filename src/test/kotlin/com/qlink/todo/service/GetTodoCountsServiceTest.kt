package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.dto.GetTodoCountsResponse
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import java.time.LocalTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class GetTodoCountsServiceTest :
    BaseServiceTest({
        val getTodoCountsService = koinGet<GetTodoCountsService>()
        val getTodosService = koinGet<GetTodosService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        suspend fun insertOwner(): Long = userRepository.insert(UserFixture.createRandomValidUser()).id!!

        suspend fun insertTodosOf(ownerId: Long): Long {
            val linkId = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = ownerId)).id!!
            val now = Clock.System.now()

            suspend fun insert(
                reminderAt: Instant? = null,
                completedAt: Instant? = null,
                repeatUntil: Instant? = null,
            ): Long =
                todoRepository
                    .insert(
                        TodoFixture.createRandomTodoOf(
                            linkId = linkId,
                            ownerId = ownerId,
                            reminderAt = reminderAt,
                            completedAt = completedAt,
                            repeatUntil = repeatUntil,
                            repeatDays = repeatUntil?.let { listOf(RepeatDay.MON) },
                            repeatTime = repeatUntil?.let { LocalTime.of(9, 0) },
                        ),
                    ).id!!

            insert()
            insert(reminderAt = now + 2.hours)
            insert(reminderAt = now - 2.hours)
            insert(reminderAt = now - 3.hours, completedAt = now - 1.hours)
            insert(completedAt = now - 5.hours)
            insert(reminderAt = now - 1.hours, repeatUntil = now + 30.days)
            insert(repeatUntil = now - 1.days)

            return insert(reminderAt = now + 4.hours)
        }

        suspend fun countByListing(
            ownerId: Long,
            status: String,
        ): Int {
            var count = 0
            var cursor: String? = null
            do {
                val page =
                    getTodosService.getTodos(
                        loginId = ownerId,
                        status = status,
                        order = null,
                        scrollRequest = ScrollRequest(cursor = cursor, size = 2),
                        isCompleted = null,
                        reminderAt = null,
                    )
                count += page.contents.size
                cursor = page.nextCursor
            } while (cursor != null)
            return count
        }

        Given("로그인 사용자의 알림 없음·지난 알림·앞으로의 알림·완료·반복 액션") {
            When("개수를 조회하면") {
                val ownerId = insertOwner()
                insertTodosOf(ownerId)
                val actual = getTodoCountsService.getTodoCounts(ownerId)

                Then("상태 규칙대로 센 active·overdue·completed이고 all은 셋의 합이다") {
                    actual shouldBe GetTodoCountsResponse(all = 8, active = 4, overdue = 2, completed = 2)
                    actual.all shouldBe actual.active + actual.overdue + actual.completed
                }
            }

            When("같은 데이터로 각 status 목록을 끝까지 조회해 세면") {
                val ownerId = insertOwner()
                insertTodosOf(ownerId)
                val actual = getTodoCountsService.getTodoCounts(ownerId)
                val listed = listOf("active", "overdue", "completed").map { countByListing(ownerId = ownerId, status = it) }

                Then("개수와 같다") {
                    listed shouldBe listOf(actual.active, actual.overdue, actual.completed)
                }
            }

            When("같은 요청을 두 번 보내면") {
                val ownerId = insertOwner()
                insertTodosOf(ownerId)
                val before = todoRepository.findAllByOwnerId(ownerId).map { it.id to it.completedAt }.toSet()
                val first = getTodoCountsService.getTodoCounts(ownerId)
                val second = getTodoCountsService.getTodoCounts(ownerId)

                Then("같은 값이고 저장된 액션은 그대로다") {
                    second shouldBe first
                    todoRepository.findAllByOwnerId(ownerId).map { it.id to it.completedAt }.toSet() shouldBe before
                }
            }

            When("미완료 액션 하나를 완료로 바꾼 뒤 다시 조회하면") {
                val ownerId = insertOwner()
                val activeTodoId = insertTodosOf(ownerId)
                val before = getTodoCountsService.getTodoCounts(ownerId)
                val todo = todoRepository.findById(activeTodoId)!!
                todoRepository.update(todo.complete(Clock.System.now()))
                val after = getTodoCountsService.getTodoCounts(ownerId)

                Then("그 액션의 이전 상태 개수가 하나 줄고 completed가 하나 늘며 all은 같다") {
                    after.active shouldBe before.active - 1
                    after.completed shouldBe before.completed + 1
                    after.overdue shouldBe before.overdue
                    after.all shouldBe before.all
                }
            }
        }

        Given("액션이 없는 로그인 사용자") {
            When("개수를 조회하면") {
                val ownerId = insertOwner()
                val actual = getTodoCountsService.getTodoCounts(ownerId)

                Then("네 값이 모두 0이다") {
                    actual shouldBe GetTodoCountsResponse(all = 0, active = 0, overdue = 0, completed = 0)
                }
            }
        }

        Given("다른 사용자의 액션이 함께 있을 때") {
            When("개수를 조회하면") {
                val ownerId = insertOwner()
                insertTodosOf(ownerId)
                insertTodosOf(insertOwner())
                val actual = getTodoCountsService.getTodoCounts(ownerId)

                Then("다른 사용자의 액션은 세지 않는다") {
                    actual.all shouldBe todoRepository.findAllByOwnerId(ownerId).size
                    actual shouldBe GetTodoCountsResponse(all = 8, active = 4, overdue = 2, completed = 2)
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("조회하면") {
                val get = suspend { getTodoCountsService.getTodoCounts(RandomFixture.randomId()) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
