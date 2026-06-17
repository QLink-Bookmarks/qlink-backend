package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.search.SearchOrder
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
import com.qlink.todo.dto.TodoSearchCursorValue
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.time.Instant
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

        Given("할 일 목록 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var link: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
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

            When("지원하지 않는 정렬이면") {
                val get =
                    suspend {
                        getTodosService.getTodos(
                            loginId = user.id!!,
                            order = "earliest",
                            scrollRequest = ScrollRequest(size = 50),
                            isCompleted = null,
                            reminderAt = null,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        get()
                    }
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

            When("커서 정렬이 요청 정렬과 다르면") {
                val cursor =
                    SearchCursorCodec.encode(
                        order = SearchOrder.EARLIEST,
                        value = TodoSearchCursorValue(id = RandomFixture.randomId()),
                    )
                val get =
                    suspend {
                        getTodosService.getTodos(
                            loginId = user.id!!,
                            order = "latest",
                            scrollRequest = ScrollRequest(cursor = cursor, size = 50),
                            isCompleted = null,
                            reminderAt = null,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
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
    })
