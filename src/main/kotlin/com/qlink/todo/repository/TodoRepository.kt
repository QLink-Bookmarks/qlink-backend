package com.qlink.todo.repository

import com.qlink.todo.domain.Todo

interface TodoRepository {
    suspend fun insert(todo: Todo): Todo

    suspend fun findById(todoId: Long): Todo?

    suspend fun update(todo: Todo): Todo
}
