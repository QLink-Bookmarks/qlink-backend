package com.qlink.todo.repository.table

import com.qlink.link.repository.table.Links
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.domain.Todo
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Todos : Table("todos") {
    val id = long("id").autoIncrement()
    val linkId = reference("link_id", Links.id, onDelete = ReferenceOption.CASCADE)
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 50)
    val reminderAt = timestamp("reminder_at").nullable()
    val repeatUntil = timestamp("repeat_until").nullable()
    val repeatDays = array("repeat_days", VarCharColumnType(3)).nullable()
    val repeatTime = time("repeat_time").nullable()
    val repeatTimezone = varchar("repeat_timezone", 100).nullable()
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("todos_link_id_idx", false, linkId)
        index("todos_owner_id_idx", false, ownerId)
    }
}

fun ResultRow.toTodoDomain(): Todo =
    Todo(
        id = this[Todos.id],
        linkId = this[Todos.linkId],
        ownerId = this[Todos.ownerId],
        title = this[Todos.title],
        reminderAt = this[Todos.reminderAt]?.toKotlinInstant(),
        repeatUntil = this[Todos.repeatUntil]?.toKotlinInstant(),
        repeatDays = this[Todos.repeatDays]?.map { RepeatDay.valueOf(it) },
        repeatTime = this[Todos.repeatTime],
        repeatTimezone = this[Todos.repeatTimezone]?.let(java.time.ZoneId::of),
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        createdAt = this[Todos.createdAt].toKotlinInstant(),
        updatedAt = this[Todos.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(todo: Todo) {
    this[Todos.linkId] = todo.linkId
    this[Todos.ownerId] = todo.ownerId
    this[Todos.title] = todo.title
    this[Todos.reminderAt] = todo.reminderAt?.toJavaInstant()
    this[Todos.repeatUntil] = todo.repeatUntil?.toJavaInstant()
    this[Todos.repeatDays] = todo.repeatDays?.map { it.name }
    this[Todos.repeatTime] = todo.repeatTime
    this[Todos.repeatTimezone] = todo.repeatTimezone?.id
    this[Todos.completedAt] = todo.completedAt?.toJavaInstant()
}

fun UpdateBuilder<*>.refreshUpdatedAt() {
    this[Todos.updatedAt] = java.time.Instant.now()
}
