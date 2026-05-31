package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.LinkSearchTodoQuery
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursor
import com.qlink.todo.dto.TodoSearchOrder

interface TodoRepository {
    suspend fun insert(todo: Todo): Todo

    suspend fun findById(todoId: Long): Todo?

    suspend fun findAllByIds(todoIds: List<Long>): List<Todo>

    suspend fun findAllByLinkId(linkId: Long): List<Todo>

    suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery>

    suspend fun findAllByLinkIdsForLinkSearch(linkIds: List<Long>): List<LinkSearchTodoQuery>

    suspend fun search(
        ownerId: Long,
        order: TodoSearchOrder,
        cursor: TodoSearchCursor?,
        size: Int,
        isCompleted: Boolean?,
        reminderAt: TodoReminderFilter?,
    ): List<SearchTodosQuery>

    suspend fun update(todo: Todo): Todo

    suspend fun deleteById(todoId: Long)
}
