package com.qlink.todo.repository

import com.qlink.common.search.longLiteral
import com.qlink.link.repository.table.Links
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.LinkSearchTodoQuery
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursor
import com.qlink.todo.dto.TodoSearchOrder
import com.qlink.todo.dto.toLinkDetailTodoQuery
import com.qlink.todo.dto.toLinkSearchTodoQuery
import com.qlink.todo.dto.toSearchTodosQuery
import com.qlink.todo.repository.table.Todos
import com.qlink.todo.repository.table.fromDomain
import com.qlink.todo.repository.table.refreshUpdatedAt
import com.qlink.todo.repository.table.toTodoDomain
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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

    override suspend fun findAllByIds(todoIds: List<Long>): List<Todo> {
        if (todoIds.isEmpty()) {
            return emptyList()
        }

        return Todos
            .selectAll()
            .where { Todos.id inList todoIds }
            .map { it.toTodoDomain() }
    }

    override suspend fun findAllByLinkId(linkId: Long): List<Todo> =
        Todos
            .selectAll()
            .where { Todos.linkId eq linkId }
            .orderBy(Todos.id to SortOrder.ASC)
            .map { it.toTodoDomain() }

    override suspend fun findAllWithReminderBetween(
        startInclusive: Instant,
        endExclusive: Instant,
    ): List<Todo> =
        Todos
            .selectAll()
            .where {
                (Todos.reminderAt greaterEq startInclusive.toJavaInstant()) and
                    (Todos.reminderAt less endExclusive.toJavaInstant()) and
                    Todos.completedAt.isNull()
            }.orderBy(Todos.reminderAt to SortOrder.ASC)
            .map { it.toTodoDomain() }

    override suspend fun findAllByLinkIdForLinkDetail(linkId: Long): List<LinkDetailTodoQuery> =
        Todos
            .select(
                Todos.id,
                Todos.title,
                Todos.completedAt,
                Todos.reminderAt,
                Todos.repeatUntil,
                Todos.repeatDays,
                Todos.repeatTime,
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
                Todos.repeatUntil,
                Todos.repeatDays,
                Todos.repeatTime,
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

    override suspend fun search(
        ownerId: Long,
        order: TodoSearchOrder,
        cursor: TodoSearchCursor?,
        size: Int,
        isCompleted: Boolean?,
        reminderAt: TodoReminderFilter?,
    ): List<SearchTodosQuery> {
        val joined =
            Todos.join(
                otherTable = Links,
                joinType = JoinType.INNER,
                additionalConstraint = { Todos.linkId eq Links.id },
            )
        val linkTitle = Links.title.alias("link_title")

        return joined
            .select(
                Todos.id,
                Todos.title,
                Todos.completedAt,
                Todos.reminderAt,
                Todos.repeatUntil,
                Todos.repeatDays,
                Todos.repeatTime,
                Todos.linkId,
                Links.url,
                linkTitle,
            ).where { Todos.ownerId eq ownerId }
            .apply {
                isCompleted?.let { completed ->
                    andWhere {
                        if (completed) {
                            Todos.completedAt.isNotNull()
                        } else {
                            Todos.completedAt.isNull()
                        }
                    }
                }
                reminderAt?.let { filter ->
                    val now = Clock.System.now().toJavaInstant()

                    andWhere {
                        when (filter) {
                            TodoReminderFilter.OVERDUE -> (Todos.reminderAt less now) and Todos.completedAt.isNull()
                            TodoReminderFilter.UPCOMING -> (Todos.reminderAt greaterEq now) and Todos.completedAt.isNull()
                        }
                    }
                }
                cursor?.value?.id?.let { cursorId ->
                    andWhere { Todos.id less longLiteral(cursorId) }
                }
            }.orderBy(Todos.id to SortOrder.DESC)
            .limit(size + 1)
            .map { it.toSearchTodosQuery(linkTitle) }
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
