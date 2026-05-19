package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery

interface TodoRepository {
    suspend fun insert(todo: Todo): Todo

    suspend fun findById(todoId: Long): Todo?

    suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery>

    suspend fun update(todo: Todo): Todo

    suspend fun deleteById(todoId: Long)
}
