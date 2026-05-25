package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.LinkSearchTodoQuery

interface TodoRepository {
    suspend fun insert(todo: Todo): Todo

    suspend fun findById(todoId: Long): Todo?

    suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery>

    suspend fun findAllByLinkIdsForLinkSearch(linkIds: List<Long>): List<LinkSearchTodoQuery>

    suspend fun update(todo: Todo): Todo

    suspend fun deleteById(todoId: Long)
}
