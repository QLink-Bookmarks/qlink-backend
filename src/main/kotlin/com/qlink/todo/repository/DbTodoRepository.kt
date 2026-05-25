package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.LinkSearchTodoQuery
import com.qlink.todo.dto.toLinkDetailTodoQuery
import com.qlink.todo.dto.toLinkSearchTodoQuery
import com.qlink.todo.repository.table.Todos
import com.qlink.todo.repository.table.fromDomain
import com.qlink.todo.repository.table.refreshUpdatedAt
import com.qlink.todo.repository.table.toTodoDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

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

    override suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery> =
        Todos
            .select(
                Todos.id,
                Todos.title,
                Todos.completedAt,
                Todos.reminderAt,
            ).where { Todos.linkId eq linkId }
            .orderBy(Todos.id to SortOrder.ASC)
            .map { it.toLinkDetailTodoQuery() }

    override suspend fun findAllByLinkIdsForLinkSearch(linkIds: List<Long>): List<LinkSearchTodoQuery> {
        if (linkIds.isEmpty()) {
            return emptyList()
        }

        return Todos
            .select(
                Todos.linkId,
                Todos.id,
                Todos.title,
                Todos.completedAt,
                Todos.reminderAt,
            ).where { Todos.linkId inList linkIds }
            .orderBy(
                Todos.linkId to SortOrder.ASC,
                Todos.id to SortOrder.ASC,
            ).map { it.toLinkSearchTodoQuery() }
            .groupBy { it.linkId }
            .entries
            .sortedBy { it.key }
            .flatMap { (_, todos) ->
                todos.take(2).map { todo ->
                    todo.copy(totalCount = todos.size)
                }
            }
    }

    override suspend fun update(todo: Todo): Todo =
        Todos
            .updateReturning(where = { Todos.id eq todo.id!! }) {
                it.fromDomain(todo)
                it.refreshUpdatedAt()
            }.single()
            .toTodoDomain()

    override suspend fun deleteById(todoId: Long) {
        Todos.deleteWhere { id eq todoId }
    }
}
