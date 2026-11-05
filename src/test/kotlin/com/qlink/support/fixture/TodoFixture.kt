package com.qlink.support.fixture

import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.CompleteTodoRequest
import com.qlink.todo.dto.UpdateTodoRequest
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object TodoFixture {
    fun createRandomTodoOf(
        linkId: Long,
        ownerId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        reminderAt: Instant? = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
        repeatUntil: Instant? = null,
        repeatDays: List<RepeatDay>? = null,
        repeatTime: LocalTime? = null,
        repeatTimezone: ZoneId? = null,
        completedAt: Instant? = null,
    ): Todo =
        Todo(
            id = RandomFixture.randomId(),
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
            completedAt = completedAt,
        )

    fun createRescheduledTodoOf(
        todo: Todo,
        reminderAt: Instant,
    ): Todo =
        todo.update(
            linkId = todo.linkId,
            title = todo.title,
            reminderAt = reminderAt,
            repeatUntil = todo.repeatUntil,
            repeatDays = todo.repeatDays,
            repeatTime = todo.repeatTime?.toString(),
            repeatTimezone = todo.repeatTimezone?.id,
            now = Clock.System.now(),
        )

    fun createOverdueCompletedTodoOf(
        linkId: Long,
        ownerId: Long,
    ): Todo =
        createRandomTodoOf(
            linkId = linkId,
            ownerId = ownerId,
            reminderAt = RandomFixture.randomPastInstant(),
            completedAt = RandomFixture.randomPastInstant(),
        )

    fun createValidUpdateTodoRequest(
        linkId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        reminderAt: Instant? = RandomFixture.futureDateTime(3, TimeUnit.DAYS).toInstant().toKotlinInstant(),
        repeatUntil: Instant? = null,
        repeatDays: List<RepeatDay>? = null,
        repeatTime: String? = null,
        repeatTimezone: String? = null,
    ): UpdateTodoRequest =
        UpdateTodoRequest(
            linkId = linkId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
        )

    fun createCompleteTodoRequest(isCompleted: Boolean): CompleteTodoRequest =
        CompleteTodoRequest(
            isCompleted = isCompleted,
        )
}
