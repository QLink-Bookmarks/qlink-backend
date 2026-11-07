package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.search.SearchOrder
import com.qlink.common.search.SortDirection
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.support.truncatedToSecond
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.domain.TodoOrderKey
import com.qlink.todo.domain.TodoStatus
import com.qlink.todo.dto.GetTodosContentResponse
import com.qlink.todo.dto.TodoSearchCursor
import com.qlink.todo.dto.TodoSearchCursorValue
import com.qlink.todo.repository.TodoRepository
import com.qlink.todo.repository.table.Todos
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class GetTodosServiceTest :
    BaseServiceTest({
        val getTodosService = koinGet<GetTodosService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()

        suspend fun insertTodo(
            linkId: Long,
            ownerId: Long,
            title: String,
            reminderAt: Instant? = null,
            repeatUntil: Instant? = null,
            repeatDays: List<RepeatDay>? = null,
            repeatTime: LocalTime? = null,
            repeatTimezone: java.time.ZoneId? = null,
            completedAt: Instant? = null,
        ) = todoRepository.insert(
            TodoFixture.createRandomTodoOf(
                linkId = linkId,
                ownerId = ownerId,
                title = title,
                reminderAt = reminderAt,
                repeatUntil = repeatUntil,
                repeatDays = repeatDays,
                repeatTime = repeatTime,
                repeatTimezone = repeatTimezone,
                completedAt = completedAt,
            ),
        )

        suspend fun getTodos(
            ownerId: Long,
            status: String? = null,
            orderKey: String? = null,
            order: String? = null,
            cursor: String? = null,
            size: Int = 50,
            isCompleted: Boolean? = null,
            reminderAt: String? = null,
        ) = getTodosService.getTodos(
            loginId = ownerId,
            status = status,
            orderKey = orderKey,
            order = order,
            scrollRequest = ScrollRequest(cursor = cursor, size = size),
            isCompleted = isCompleted,
            reminderAt = reminderAt,
        )

        suspend fun allPages(
            ownerId: Long,
            orderKey: String? = null,
            order: String? = null,
            size: Int,
        ): List<List<Long>> {
            val pages = mutableListOf<List<Long>>()
            var cursor: String? = null
            do {
                val page = getTodos(ownerId = ownerId, orderKey = orderKey, order = order, cursor = cursor, size = size)
                pages += page.ids()
                cursor = page.nextCursor
            } while (cursor != null)
            return pages
        }

        suspend fun insertOwner(): TodoOwner {
            val owner = userRepository.insert(UserFixture.createRandomValidUser())
            val ownerLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!))

            return TodoOwner(userId = owner.id!!, linkId = ownerLink.id!!)
        }

        suspend fun insertTodoOf(
            owner: TodoOwner,
            reminderAt: Instant? = null,
            completedAt: Instant? = null,
            repeatUntil: Instant? = null,
            createdAt: Instant? = null,
        ): Long {
            val todo =
                todoRepository.insert(
                    TodoFixture.createRandomTodoOf(
                        linkId = owner.linkId,
                        ownerId = owner.userId,
                        reminderAt = reminderAt,
                        completedAt = completedAt,
                        repeatUntil = repeatUntil,
                        repeatDays = repeatUntil?.let { listOf(RepeatDay.MON) },
                        repeatTime = repeatUntil?.let { LocalTime.of(9, 0) },
                    ),
                )
            createdAt?.let { updateCreatedAt(todoId = todo.id!!, createdAt = it) }

            return todo.id!!
        }

        suspend fun insertMixedTodos(): MixedTodos {
            val owner = insertOwner()
            val now = Clock.System.now()
            val base = RandomFixture.randomPastInstant()

            suspend fun insertAt(
                createdHours: Int,
                reminderAt: Instant? = null,
                completedAt: Instant? = null,
                repeatUntil: Instant? = null,
            ): Long =
                insertTodoOf(
                    owner = owner,
                    reminderAt = reminderAt,
                    completedAt = completedAt,
                    repeatUntil = repeatUntil,
                    createdAt = base + createdHours.hours,
                )

            return MixedTodos(
                ownerId = owner.userId,
                noReminder = insertAt(createdHours = 3),
                overdue = insertAt(createdHours = 0, reminderAt = now - 2.hours),
                upcoming = insertAt(createdHours = 6, reminderAt = now + 2.hours),
                upcomingLater = insertAt(createdHours = 1, reminderAt = now + 5.hours),
                completedEarlier = insertAt(createdHours = 5, reminderAt = now - 30.hours, completedAt = now - 20.hours),
                completedLater = insertAt(createdHours = 2, completedAt = now - 10.hours),
                repeatOverdue = insertAt(createdHours = 4, reminderAt = now - 1.hours, repeatUntil = now + 30.days),
                repeatEnded = insertAt(createdHours = 7, repeatUntil = now - 1.days),
            )
        }

        Given("할 일 목록 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            thumbnailUrl = RandomFixture.randomPreviewUrl(),
                            faviconUrl = RandomFixture.randomPreviewUrl(kind = "favicon"),
                        ),
                    )
            }

            When("최신순 목록 조회를") {
                val expectedLinkId = link.id!!
                val expectedLinkUrl = link.url
                val expectedLinkTitle = link.title
                val first = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "first todo")
                val second = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "second todo")
                val actual =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = null,
                        reminderAt = null,
                    )

                Then("링크 요약 정보와 함께 반환한다") {
                    actual.isEmpty shouldBe false
                    actual.hasNext shouldBe false
                    actual.nextCursor shouldBe null
                    actual.contents.map { it.id } shouldContainExactly listOf(second.id!!, first.id!!)
                    actual.contents.first().linkId shouldBe expectedLinkId
                    actual.contents.first().linkUrl shouldBe expectedLinkUrl
                    actual.contents.first().linkTitle shouldBe expectedLinkTitle
                    val storedLink = linkRepository.findById(expectedLinkId)!!
                    actual.contents.first().linkThumbnailUrl shouldBe storedLink.thumbnailUrl
                    actual.contents.first().linkFaviconUrl shouldBe storedLink.faviconUrl
                }
            }

            When("커서 기반 다음 페이지 조회를") {
                val first = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "first todo")
                val second = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "second todo")
                val firstPage =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 1),
                        isCompleted = null,
                        reminderAt = null,
                    )
                val secondPage =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(cursor = firstPage.nextCursor, size = 1),
                        isCompleted = null,
                        reminderAt = null,
                    )

                Then("다음 할 일부터 반환한다") {
                    firstPage.contents.map { it.id } shouldContainExactly listOf(second.id!!)
                    firstPage.hasNext shouldBe true
                    secondPage.contents.map { it.id } shouldContainExactly listOf(first.id!!)
                    secondPage.hasNext shouldBe false
                }
            }

            When("반복 설정이 있는 할 일 목록 조회를") {
                val repeatUntil = RandomFixture.futureDateTime(30, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val todo =
                    insertTodo(
                        linkId = link.id!!,
                        ownerId = user.id!!,
                        title = "repeat todo",
                        repeatUntil = repeatUntil,
                        repeatDays = listOf(RepeatDay.TUE, RepeatDay.THU),
                        repeatTime = LocalTime.of(8, 45),
                        repeatTimezone = java.time.ZoneId.of("Asia/Seoul"),
                    )
                val actual =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = null,
                        reminderAt = null,
                    )

                Then("반복 종료일, 요일, 시간을 반환한다") {
                    val content = actual.contents.first { it.id == todo.id!! }

                    content.repeatUntil.truncatedToSecond() shouldBe repeatUntil.truncatedToSecond()
                    content.repeatDays shouldBe listOf(RepeatDay.TUE, RepeatDay.THU)
                    content.repeatTime shouldBe "08:45"
                }
            }

            When("완료 여부 필터를 적용하면") {
                val completedAt = RandomFixture.randomDateTime().toInstant().toKotlinInstant()
                val completed = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "done", completedAt = completedAt)
                val incomplete = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "todo")
                val completedResult =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = true,
                        reminderAt = null,
                    )
                val incompleteResult =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = false,
                        reminderAt = null,
                    )

                Then("조건에 맞는 할 일만 반환한다") {
                    completedResult.contents.map { it.id } shouldContainExactly listOf(completed.id!!)
                    completedResult.contents
                        .first()
                        .completedAt
                        .truncatedToSecond() shouldBe completedAt.truncatedToSecond()
                    incompleteResult.contents.map { it.id } shouldContainExactly listOf(incomplete.id!!)
                    incompleteResult.contents.first().completedAt shouldBe null
                }
            }

            When("알림 예정 필터를 적용하면") {
                val upcomingAt = RandomFixture.futureDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val overdueAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val upcoming = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "upcoming", reminderAt = upcomingAt)
                insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "overdue", reminderAt = overdueAt)
                val actual =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = null,
                        reminderAt = "upcoming",
                    )

                Then("미완료이면서 현재 이후 알림인 할 일만 반환한다") {
                    actual.contents.map { it.id } shouldContainExactly listOf(upcoming.id!!)
                }
            }

            When("기한 지남 필터를 적용하면") {
                val upcomingAt = RandomFixture.futureDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                val overdueAt = RandomFixture.pastDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant()
                insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "upcoming", reminderAt = upcomingAt)
                val overdue = insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "overdue", reminderAt = overdueAt)
                val completedOverdue =
                    insertTodo(
                        linkId = link.id!!,
                        ownerId = user.id!!,
                        title = "completed overdue",
                        reminderAt = overdueAt,
                        completedAt = overdueAt,
                    )
                val actual =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = null,
                        reminderAt = "overdue",
                    )

                Then("미완료이면서 현재 이전 알림인 할 일만 반환한다") {
                    actual.contents.map { it.id } shouldContainExactly listOf(overdue.id!!)
                    actual.contents.map { it.id }.contains(completedOverdue.id!!) shouldBe false
                }
            }

            When("페이지 크기가 0 이하이면") {
                repeat(51) {
                    insertTodo(linkId = link.id!!, ownerId = user.id!!, title = "todo $it")
                }
                val actual =
                    getTodosService.getTodos(
                        loginId = user.id!!,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 0),
                        isCompleted = null,
                        reminderAt = null,
                    )

                Then("기본 크기 50을 사용한다") {
                    actual.contents shouldHaveSize 50
                    actual.hasNext shouldBe true
                }
            }

            When("커서가 올바르지 않으면") {
                val get =
                    suspend {
                        getTodosService.getTodos(
                            loginId = user.id!!,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = "not-base64", size = 50),
                            isCompleted = null,
                            reminderAt = null,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }

            When("알림 필터가 올바르지 않으면") {
                val get =
                    suspend {
                        getTodosService.getTodos(
                            loginId = user.id!!,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 50),
                            isCompleted = null,
                            reminderAt = "soon",
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_FILTER.message) {
                        get()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getTodosService.getTodos(
                            loginId = RandomFixture.randomId(),
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 50),
                            isCompleted = null,
                            reminderAt = null,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.TODO_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }

        Given("로그인 사용자의 알림 없음·지난 알림·앞으로의 알림·완료·반복 액션") {
            When("정렬 없이 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId)
                val legacy = getTodos(ownerId = todos.ownerId, order = "latest")

                Then("만든 날 최신순, 같으면 id 내림차순이고 기존 latest와 같은 순서다") {
                    actual.ids() shouldContainExactly todos.createdAtDesc
                    legacy.ids() shouldContainExactly todos.createdAtDesc
                }
            }

            When("만든 날 오름차순으로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, orderKey = "created_at", order = "asc")

                Then("오래된 액션부터 나온다") {
                    actual.ids() shouldContainExactly todos.createdAtDesc.reversed()
                }
            }

            When("알림일 오름차순으로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, orderKey = "reminder_at")

                Then("알림 임박 순이고 알림 없는 액션은 맨 뒤다") {
                    actual.ids() shouldContainExactly
                        listOf(todos.completedEarlier, todos.overdue, todos.repeatOverdue, todos.upcoming, todos.upcomingLater) +
                        todos.withoutReminder.sorted()
                }
            }

            When("알림일 내림차순으로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, orderKey = "reminder_at", order = "desc")

                Then("알림 늦은 순이고 알림 없는 액션은 여전히 맨 뒤다") {
                    actual.ids() shouldContainExactly
                        listOf(todos.upcomingLater, todos.upcoming, todos.repeatOverdue, todos.overdue, todos.completedEarlier) +
                        todos.withoutReminder.sortedDescending()
                }
            }

            When("완료일 내림차순·오름차순으로 조회하면") {
                val todos = insertMixedTodos()
                val desc = getTodos(ownerId = todos.ownerId, orderKey = "completed_at")
                val asc = getTodos(ownerId = todos.ownerId, orderKey = "completed_at", order = "asc")

                Then("완료일 순이고 미완료 액션은 두 방향 모두 맨 뒤다") {
                    desc.ids() shouldContainExactly
                        listOf(todos.completedLater, todos.completedEarlier) + todos.incomplete.sortedDescending()
                    asc.ids() shouldContainExactly listOf(todos.completedEarlier, todos.completedLater) + todos.incomplete.sorted()
                }
            }

            When("필터 없이 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId)

                Then("각 액션의 status가 상태 규칙대로이고 createdAt이 저장된 값이다") {
                    actual.contents.associate { it.id to it.status } shouldBe todos.statusById
                    actual.contents.forEach { content ->
                        content.createdAt shouldBe todoRepository.findById(content.id)!!.createdAt
                    }
                }
            }

            When("status=active로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "active")

                Then("미완료이면서 알림 시각이 지나지 않은 액션(알림 없음 포함)만 나온다") {
                    actual.ids() shouldContainExactlyInAnyOrder todos.active
                    actual.contents.map { it.status }.toSet() shouldBe setOf(TodoStatus.ACTIVE)
                }
            }

            When("status=overdue로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "overdue")

                Then("미완료이면서 알림 시각이 지난 액션만 나온다") {
                    actual.ids() shouldContainExactlyInAnyOrder todos.overdueIds
                    actual.contents.map { it.status }.toSet() shouldBe setOf(TodoStatus.OVERDUE)
                }
            }

            When("status=completed로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "completed")

                Then("완료된 액션만 나온다") {
                    actual.ids() shouldContainExactlyInAnyOrder todos.completed
                    actual.contents.map { it.status }.toSet() shouldBe setOf(TodoStatus.COMPLETED)
                }
            }

            When("status를 대문자로 보내면") {
                val todos = insertMixedTodos()
                val upper = getTodos(ownerId = todos.ownerId, status = "OVERDUE")
                val lower = getTodos(ownerId = todos.ownerId, status = "overdue")

                Then("소문자로 보낸 것과 같은 결과다") {
                    upper.ids() shouldContainExactly lower.ids()
                }
            }

            When("status=completed와 완료일 내림차순으로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "completed", orderKey = "completed_at", order = "desc")

                Then("완료된 액션만 최근 완료 순으로 나온다") {
                    actual.ids() shouldContainExactly listOf(todos.completedLater, todos.completedEarlier)
                }
            }

            When("기존 isCompleted·reminderAt 필터로 조회하면") {
                val todos = insertMixedTodos()
                val completed = getTodos(ownerId = todos.ownerId, isCompleted = true)
                val overdue = getTodos(ownerId = todos.ownerId, reminderAt = "overdue")

                Then("지금과 같은 결과다") {
                    completed.ids() shouldContainExactlyInAnyOrder todos.completed
                    overdue.ids() shouldContainExactlyInAnyOrder todos.overdueIds
                }
            }

            When("status와 isCompleted를 함께 보내면") {
                val todos = insertMixedTodos()
                val get = suspend { getTodos(ownerId = todos.ownerId, status = "active", isCompleted = false) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_FILTER.message) {
                        get()
                    }
                }
            }

            When("status와 reminderAt을 함께 보내면") {
                val todos = insertMixedTodos()
                val get = suspend { getTodos(ownerId = todos.ownerId, status = "overdue", reminderAt = "overdue") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_FILTER.message) {
                        get()
                    }
                }
            }

            When("모르는 status 값이면") {
                val todos = insertMixedTodos()
                val get = suspend { getTodos(ownerId = todos.ownerId, status = "upcoming") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_FILTER.message) {
                        get()
                    }
                }
            }
        }

        Given("다음 회차 시각이 지났지만 아직 발송 전인 미완료 반복 액션과 반복이 끝나 알림 시각이 없는 반복 액션") {
            When("status=overdue로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "overdue")

                Then("회차 시각이 지난 반복 액션만 OVERDUE로 나온다") {
                    actual.ids() shouldContain todos.repeatOverdue
                    actual.ids() shouldNotContain todos.repeatEnded
                    actual.contents.first { it.id == todos.repeatOverdue }.status shouldBe TodoStatus.OVERDUE
                }
            }

            When("status=active로 조회하면") {
                val todos = insertMixedTodos()
                val actual = getTodos(ownerId = todos.ownerId, status = "active")

                Then("반복이 끝난 액션이 ACTIVE로 나온다") {
                    actual.ids() shouldContain todos.repeatEnded
                    actual.ids() shouldNotContain todos.repeatOverdue
                    actual.contents.first { it.id == todos.repeatEnded }.status shouldBe TodoStatus.ACTIVE
                }
            }
        }

        Given("같은 알림 시각·같은 만든 날의 액션들") {
            When("알림일 오름차순·내림차순으로 조회하면") {
                val owner = insertOwner()
                val reminderAt = Clock.System.now() + 3.hours
                val ids = List(3) { insertTodoOf(owner = owner, reminderAt = reminderAt) }
                val asc = getTodos(ownerId = owner.userId, orderKey = "reminder_at", order = "asc")
                val desc = getTodos(ownerId = owner.userId, orderKey = "reminder_at", order = "desc")
                val ascPages = allPages(ownerId = owner.userId, orderKey = "reminder_at", order = "asc", size = 1)

                Then("같은 알림 시각끼리는 id가 각각 오름차순·내림차순이다") {
                    asc.ids() shouldContainExactly ids.sorted()
                    desc.ids() shouldContainExactly ids.sortedDescending()
                    ascPages.flatten() shouldContainExactly ids.sorted()
                }
            }

            When("만든 날 오름차순·내림차순으로 조회하면") {
                val owner = insertOwner()
                val createdAt = RandomFixture.randomPastInstant()
                val ids = List(3) { insertTodoOf(owner = owner, createdAt = createdAt) }
                val asc = getTodos(ownerId = owner.userId, orderKey = "created_at", order = "asc")
                val desc = getTodos(ownerId = owner.userId, orderKey = "created_at", order = "desc")
                val descPages = allPages(ownerId = owner.userId, orderKey = "created_at", order = "desc", size = 1)

                Then("같은 만든 날끼리는 id가 각각 오름차순·내림차순이다") {
                    asc.ids() shouldContainExactly ids.sorted()
                    desc.ids() shouldContainExactly ids.sortedDescending()
                    descPages.flatten() shouldContainExactly ids.sortedDescending()
                }
            }
        }

        Given("한 페이지보다 많은 액션(알림 있는 것과 없는 것이 섞임)") {
            When("알림일 오름차순으로 다음 커서를 따라 끝까지 조회하면") {
                val todos = insertMixedTodos()
                val expected = getTodos(ownerId = todos.ownerId, orderKey = "reminder_at").ids()
                val pages = listOf(1, 2, 3).map { size -> allPages(ownerId = todos.ownerId, orderKey = "reminder_at", size = size) }

                Then("알림 있는 구간에서 알림 없는 구간으로 넘어가는 경계를 포함해 겹치거나 빠지는 액션이 없다") {
                    pages.forEach { it.flatten() shouldContainExactly expected }
                }
            }

            When("알림일 내림차순으로 페이지 경계가 알림 없는 구간 안에 오도록 조회하면") {
                val todos = insertMixedTodos()
                val expected = getTodos(ownerId = todos.ownerId, orderKey = "reminder_at", order = "desc").ids()
                val pages = allPages(ownerId = todos.ownerId, orderKey = "reminder_at", order = "desc", size = 6)
                val pagesBySize =
                    listOf(1, 2, 3, 5).map { size ->
                        allPages(ownerId = todos.ownerId, orderKey = "reminder_at", order = "desc", size = size)
                    }

                Then("다음 페이지가 알림 없는 구간에서 겹치거나 빠지지 않고 이어진다") {
                    pages.first().last() shouldBe todos.withoutReminder.max()
                    pages.flatten() shouldContainExactly expected
                    pagesBySize.forEach { it.flatten() shouldContainExactly expected }
                }
            }

            When("완료일 내림차순으로 다음 커서를 따라 끝까지 조회하면") {
                val todos = insertMixedTodos()
                val expected = getTodos(ownerId = todos.ownerId, orderKey = "completed_at").ids()
                val pages = listOf(1, 2, 3).map { size -> allPages(ownerId = todos.ownerId, orderKey = "completed_at", size = size) }

                Then("완료·미완료 경계를 포함해 겹치거나 빠지는 액션이 없다") {
                    pages.forEach { it.flatten() shouldContainExactly expected }
                }
            }

            When("기존 형식 id만 든 LATEST 커서로 정렬 없이 조회하면") {
                val todos = insertMixedTodos()
                val cursorId = todos.createdAtDesc[2]
                val legacyCursor = SearchCursorCodec.encode(order = SearchOrder.LATEST, value = TodoSearchCursorValue(id = cursorId))
                val actual = getTodos(ownerId = todos.ownerId, cursor = legacyCursor)

                Then("커서 액션 다음부터 만든 날 최신순으로 이어진다") {
                    actual.ids() shouldContainExactly todos.createdAtDesc.drop(3)
                }
            }

            When("기존 형식 id 커서의 액션이 삭제되었으면") {
                val todos = insertMixedTodos()
                val deletedId = todos.createdAtDesc[2]
                todoRepository.deleteById(deletedId)
                val legacyCursor = SearchCursorCodec.encode(order = SearchOrder.LATEST, value = TodoSearchCursorValue(id = deletedId))
                val get = suspend { getTodos(ownerId = todos.ownerId, cursor = legacyCursor) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_MALFORMED.message) {
                        get()
                    }
                }
            }

            When("커서에 id가 없으면") {
                val todos = insertMixedTodos()
                val cursor =
                    Base64CursorCodec.encode(
                        TodoSearchCursor(
                            orderKey = TodoOrderKey.REMINDER_AT,
                            direction = SortDirection.ASC,
                            value = TodoSearchCursorValue(reminderAt = Clock.System.now()),
                        ),
                    )
                val get = suspend { getTodos(ownerId = todos.ownerId, orderKey = "reminder_at", cursor = cursor) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_FIELD_MISSING.message) {
                        get()
                    }
                }
            }
        }

        Given("목록을 읽은 뒤 상태를 정하기 전에 다른 기기에서 지운 액션") {
            When("조회하면") {
                val todos = insertMixedTodos()
                val deletedId = todos.createdAtDesc.first()
                val racingService =
                    GetTodosService(
                        tx = koinGet(),
                        todoRepository =
                            object : TodoRepository by todoRepository {
                                override suspend fun findAllByIds(todoIds: List<Long>): List<Todo> {
                                    todoRepository.deleteById(deletedId)
                                    return todoRepository.findAllByIds(todoIds)
                                }
                            },
                        userRepository = userRepository,
                    )
                val actual =
                    racingService.getTodos(
                        loginId = todos.ownerId,
                        scrollRequest = ScrollRequest(size = 50),
                        isCompleted = null,
                        reminderAt = null,
                    )

                Then("지운 액션만 빠지고 나머지는 그대로 나온다") {
                    actual.ids() shouldContainExactly todos.createdAtDesc.drop(1)
                }
            }
        }

        Given("상태 필터에 맞는 액션이 없을 때") {
            When("status=overdue로 조회하면") {
                val owner = insertOwner()
                insertTodoOf(owner = owner, reminderAt = Clock.System.now() + 3.hours)
                insertTodoOf(owner = owner, reminderAt = null)
                val actual = getTodos(ownerId = owner.userId, status = "overdue")

                Then("빈 목록이고 다음 커서가 없다") {
                    actual.isEmpty shouldBe true
                    actual.contents.shouldBeEmpty()
                    actual.hasNext shouldBe false
                    actual.nextCursor shouldBe null
                }
            }
        }

        Given("다른 사용자의 액션이 함께 있을 때") {
            When("어떤 상태·기준·방향으로 조회해도") {
                val mine = insertMixedTodos()
                val others = insertMixedTodos()
                val requests =
                    listOf(null, "active", "overdue", "completed").flatMap { status ->
                        listOf("reminder_at", "created_at", "completed_at").flatMap { orderKey ->
                            listOf("asc", "desc").map { order -> Triple(status, orderKey, order) }
                        }
                    }
                val seen =
                    requests.flatMap { (status, orderKey, order) ->
                        getTodos(ownerId = mine.ownerId, status = status, orderKey = orderKey, order = order).ids()
                    }

                Then("다른 사용자의 액션은 나오지 않는다") {
                    seen.toSet() shouldBe mine.statusById.keys
                    seen.none { it in others.statusById.keys } shouldBe true
                }
            }
        }
    })

private fun ScrollResponse<GetTodosContentResponse>.ids(): List<Long> = contents.map { it.id }

private fun updateCreatedAt(
    todoId: Long,
    createdAt: Instant,
) {
    Todos.update({ Todos.id eq todoId }) { it[Todos.createdAt] = createdAt.toJavaInstant() }
}

private class TodoOwner(
    val userId: Long,
    val linkId: Long,
)

private class MixedTodos(
    val ownerId: Long,
    val noReminder: Long,
    val overdue: Long,
    val upcoming: Long,
    val upcomingLater: Long,
    val completedEarlier: Long,
    val completedLater: Long,
    val repeatOverdue: Long,
    val repeatEnded: Long,
) {
    val createdAtDesc: List<Long> =
        listOf(repeatEnded, upcoming, completedEarlier, repeatOverdue, noReminder, completedLater, upcomingLater, overdue)
    val withoutReminder: List<Long> = listOf(noReminder, completedLater, repeatEnded)
    val incomplete: List<Long> = listOf(noReminder, overdue, upcoming, upcomingLater, repeatOverdue, repeatEnded)
    val active: List<Long> = listOf(noReminder, upcoming, upcomingLater, repeatEnded)
    val overdueIds: List<Long> = listOf(overdue, repeatOverdue)
    val completed: List<Long> = listOf(completedEarlier, completedLater)
    val statusById: Map<Long, TodoStatus> =
        active.associateWith { TodoStatus.ACTIVE } +
            overdueIds.associateWith { TodoStatus.OVERDUE } +
            completed.associateWith { TodoStatus.COMPLETED }
}
