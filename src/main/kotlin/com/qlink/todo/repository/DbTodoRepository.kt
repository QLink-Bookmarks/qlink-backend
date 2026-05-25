package com.qlink.todo.repository

import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.LinkSearchTodoQuery
import com.qlink.todo.dto.toLinkDetailTodoQuery
import com.qlink.todo.repository.table.Todos
import com.qlink.todo.repository.table.fromDomain
import com.qlink.todo.repository.table.refreshUpdatedAt
import com.qlink.todo.repository.table.toTodoDomain
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.toKotlinInstant

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

        val placeholders = List(linkIds.size) { "?" }.joinToString(", ")
        val sql =
            """
            SELECT
                base.link_id,
                base.id,
                base.title,
                base.completed_at,
                base.reminder_at,
                base.total_count
            FROM (
                SELECT
                    t.link_id,
                    t.id,
                    t.title,
                    t.completed_at,
                    t.reminder_at,
                    count(*) OVER (PARTITION BY t.link_id) AS total_count,
                    row_number() OVER (PARTITION BY t.link_id ORDER BY t.id ASC) AS row_number
                FROM todos t
                WHERE t.link_id IN ($placeholders)
            ) base
            WHERE base.row_number <= 2
            ORDER BY base.link_id ASC, base.id ASC
            """.trimIndent()
        val args = linkIds.map { LongColumnType() to it as Any? }

        return TransactionManager
            .current()
            .exec(sql, args) { resultSet ->
                generateSequence {
                    if (resultSet.next()) {
                        LinkSearchTodoQuery(
                            linkId = resultSet.getLong("link_id"),
                            id = resultSet.getLong("id"),
                            title = resultSet.getString("title"),
                            completedAt = resultSet.getTimestamp("completed_at")?.toInstant()?.toKotlinInstant(),
                            reminderAt = resultSet.getTimestamp("reminder_at")?.toInstant()?.toKotlinInstant(),
                            totalCount = resultSet.getInt("total_count"),
                        )
                    } else {
                        null
                    }
                }.toList()
            }.orEmpty()
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
