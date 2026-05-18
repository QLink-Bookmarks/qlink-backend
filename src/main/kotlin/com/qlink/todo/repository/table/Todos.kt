package com.qlink.todo.repository.table

import com.qlink.link.repository.table.Links
import com.qlink.todo.domain.Todo
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Todos : Table("todos") {
    val id = long("id").autoIncrement()
    val linkId = reference("link_id", Links.id, onDelete = ReferenceOption.CASCADE)
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 50)
    val reminderAt = timestamp("reminder_at").nullable()
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
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        createdAt = this[Todos.createdAt].toKotlinInstant(),
        updatedAt = this[Todos.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(todo: Todo) {
    this[Todos.linkId] = todo.linkId
    this[Todos.ownerId] = todo.ownerId
    this[Todos.title] = todo.title
    this[Todos.reminderAt] = todo.reminderAt?.toJavaInstant()
    this[Todos.completedAt] = todo.completedAt?.toJavaInstant()
}
