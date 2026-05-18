package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.table.Todos
import com.qlink.todo.repository.table.fromDomain
import com.qlink.todo.repository.table.toTodoDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbTodoRepository : TodoRepository {
    override suspend fun insert(todo: Todo): Todo =
        Todos
            .insertReturning { it.fromDomain(todo) }
            .single()
            .toTodoDomain()

    override suspend fun findById(todoId: Long): Todo? =
        Todos
            .selectAll()
            .where { Todos.id eq todoId }
            .singleOrNull()
            ?.toTodoDomain()
}
