package com.qlink.support.fixture

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.CompleteTodoRequest
import com.qlink.todo.dto.UpdateTodoRequest
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object TodoFixture {
    fun createRandomTodoOf(
        linkId: Long,
        ownerId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        reminderAt: Instant? = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
        completedAt: Instant? = null,
    ): Todo =
        Todo(
            id = RandomFixture.randomId(),
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            completedAt = completedAt,
        )

    fun createValidUpdateTodoRequest(
        linkId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        reminderAt: Instant? = RandomFixture.randomDateTime().toInstant().toKotlinInstant(),
    ): UpdateTodoRequest =
        UpdateTodoRequest(
            linkId = linkId,
            title = title,
            reminderAt = reminderAt,
        )

    fun createCompleteTodoRequest(isCompleted: Boolean): CompleteTodoRequest =
        CompleteTodoRequest(
            isCompleted = isCompleted,
        )
}
