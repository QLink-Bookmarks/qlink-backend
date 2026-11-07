package com.qlink.todo.repository

import com.qlink.common.search.SortDirection
import com.qlink.common.search.longLiteral
import com.qlink.link.repository.table.Links
import com.qlink.todo.domain.Todo
import com.qlink.todo.domain.TodoOrderKey
import com.qlink.todo.domain.TodoSort
import com.qlink.todo.domain.TodoStatus
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursor
import com.qlink.todo.dto.TodoSearchCursorValue
import com.qlink.todo.dto.toLinkDetailTodoQuery
import com.qlink.todo.dto.toSearchTodosQuery
import com.qlink.todo.repository.table.Todos
import com.qlink.todo.repository.table.fromDomain
import com.qlink.todo.repository.table.refreshUpdatedAt
import com.qlink.todo.repository.table.toTodoDomain
import org.jetbrains.exposed.v1.core.EqOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.GreaterOp
import org.jetbrains.exposed.v1.core.IsNullOp
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LessOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Instant
import kotlin.time.toJavaInstant
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

    override suspend fun findByIdForShare(todoId: Long): Todo? =
        Todos
            .selectAll()
            .where { Todos.id eq todoId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForShare)
            .singleOrNull()
            ?.toTodoDomain()

    override suspend fun findAllByLinkIdsForUpdate(linkIds: List<Long>): List<Todo> =
        Todos
            .selectAll()
            .where { Todos.linkId inList linkIds.distinct().sorted() }
            .orderBy(Todos.id to SortOrder.ASC)
            .forUpdate()
            .map { it.toTodoDomain() }

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

    override suspend fun findAllByLinkIds(linkIds: List<Long>): List<Todo> {
        if (linkIds.isEmpty()) {
            return emptyList()
        }

        return Todos
            .selectAll()
            .where { Todos.linkId inList linkIds }
            .map { it.toTodoDomain() }
    }

    override suspend fun findAllByOwnerId(ownerId: Long): List<Todo> =
        Todos
            .selectAll()
            .where { Todos.ownerId eq ownerId }
            .map { it.toTodoDomain() }

    override suspend fun findCreatedAtById(todoId: Long): Instant? =
        Todos
            .select(Todos.createdAt)
            .where { Todos.id eq todoId }
            .singleOrNull()
            ?.let { it[Todos.createdAt].toKotlinInstant() }

    override suspend fun search(
        ownerId: Long,
        sort: TodoSort,
        cursor: TodoSearchCursor?,
        size: Int,
        status: TodoStatus?,
        isCompleted: Boolean?,
        reminderAt: TodoReminderFilter?,
        now: Instant,
    ): List<SearchTodosQuery> {
        val joined =
            Todos.join(
                otherTable = Links,
                joinType = JoinType.INNER,
                additionalConstraint = { Todos.linkId eq Links.id },
            )
        val linkTitle = Links.title.alias("link_title")
        val orderColumn = sort.orderKey.column()
        val sortOrder = sort.direction.sortOrder()
        val nowValue = now.toJavaInstant()

        return joined
            .select(
                Todos.id,
                Todos.title,
                Todos.completedAt,
                Todos.reminderAt,
                Todos.repeatUntil,
                Todos.repeatDays,
                Todos.repeatTime,
                Todos.createdAt,
                Todos.linkId,
                Links.url,
                linkTitle,
                Links.thumbnailUrl,
                Links.faviconUrl,
            ).where { Todos.ownerId eq ownerId }
            .apply {
                status?.let { filter ->
                    andWhere { filter.condition(nowValue) }
                }
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
                    andWhere {
                        when (filter) {
                            TodoReminderFilter.OVERDUE -> (Todos.reminderAt less nowValue) and Todos.completedAt.isNull()
                            TodoReminderFilter.UPCOMING -> (Todos.reminderAt greaterEq nowValue) and Todos.completedAt.isNull()
                        }
                    }
                }
                cursor?.value?.let { cursorValue ->
                    cursorValue.id?.let { cursorId ->
                        andWhere {
                            keysetAfter(
                                column = orderColumn,
                                value = cursorValue.valueOf(sort.orderKey)?.toJavaInstant(),
                                id = cursorId,
                                direction = sort.direction,
                            )
                        }
                    }
                }
            }.orderBy(orderColumn to sortOrder, Todos.id to sortOrder)
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

    private fun TodoStatus.condition(now: java.time.Instant): Op<Boolean> =
        when (this) {
            TodoStatus.COMPLETED -> Todos.completedAt.isNotNull()
            TodoStatus.OVERDUE -> Todos.completedAt.isNull() and (Todos.reminderAt less now)
            TodoStatus.ACTIVE -> Todos.completedAt.isNull() and (Todos.reminderAt.isNull() or (Todos.reminderAt greaterEq now))
        }

    private fun TodoOrderKey.column(): Expression<*> =
        when (this) {
            TodoOrderKey.REMINDER_AT -> Todos.reminderAt
            TodoOrderKey.CREATED_AT -> Todos.createdAt
            TodoOrderKey.COMPLETED_AT -> Todos.completedAt
        }

    private fun TodoSearchCursorValue.valueOf(orderKey: TodoOrderKey): Instant? =
        when (orderKey) {
            TodoOrderKey.REMINDER_AT -> reminderAt
            TodoOrderKey.CREATED_AT -> createdAt
            TodoOrderKey.COMPLETED_AT -> completedAt
        }

    private fun SortDirection.sortOrder(): SortOrder =
        when (this) {
            SortDirection.ASC -> SortOrder.ASC_NULLS_LAST
            SortDirection.DESC -> SortOrder.DESC_NULLS_LAST
        }

    private fun keysetAfter(
        column: Expression<*>,
        value: java.time.Instant?,
        id: Long,
        direction: SortDirection,
    ): Op<Boolean> {
        val idBeyond = beyond(expression = Todos.id, cursorValue = longLiteral(id), direction = direction)

        if (value == null) {
            return IsNullOp(column) and idBeyond
        }

        val cursorValue = QueryParameter(value, JavaInstantColumnType())

        return beyond(expression = column, cursorValue = cursorValue, direction = direction) or
            (EqOp(column, cursorValue) and idBeyond) or
            IsNullOp(column)
    }

    private fun beyond(
        expression: Expression<*>,
        cursorValue: Expression<*>,
        direction: SortDirection,
    ): Op<Boolean> =
        when (direction) {
            SortDirection.ASC -> GreaterOp(expression, cursorValue)
            SortDirection.DESC -> LessOp(expression, cursorValue)
        }
}
