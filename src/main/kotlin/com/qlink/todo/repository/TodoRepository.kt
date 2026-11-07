package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.domain.TodoSort
import com.qlink.todo.domain.TodoStatus
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursor
import kotlin.time.Instant

interface TodoRepository {
    suspend fun insert(todo: Todo): Todo

    suspend fun findById(todoId: Long): Todo?

    suspend fun findByIdForShare(todoId: Long): Todo?

    suspend fun findAllByLinkIdsForUpdate(linkIds: List<Long>): List<Todo>

    suspend fun findAllByIds(todoIds: List<Long>): List<Todo>

    suspend fun findAllByLinkId(linkId: Long): List<Todo>

    suspend fun findAllWithReminderBetween(
        startInclusive: Instant,
        endExclusive: Instant,
    ): List<Todo>

    suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery>

    suspend fun findAllByLinkIds(linkIds: List<Long>): List<Todo>

    suspend fun findAllByOwnerId(ownerId: Long): List<Todo>

    suspend fun findCreatedAtById(todoId: Long): Instant?

    suspend fun search(
        ownerId: Long,
        sort: TodoSort,
        cursor: TodoSearchCursor?,
        size: Int,
        status: TodoStatus?,
        isCompleted: Boolean?,
        reminderAt: TodoReminderFilter?,
        now: Instant,
    ): List<SearchTodosQuery>

    suspend fun update(todo: Todo): Todo

    suspend fun deleteById(todoId: Long)
}
