@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import com.qlink.todo.repository.table.Todos
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class LinkDetailTodoQuery(
    val id: Long,
    val title: String,
    val completedAt: Instant?,
    val reminderAt: Instant?,
)

fun ResultRow.toLinkDetailTodoQuery(): LinkDetailTodoQuery =
    LinkDetailTodoQuery(
        id = this[Todos.id],
        title = this[Todos.title],
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        reminderAt = this[Todos.reminderAt]?.toKotlinInstant(),
    )
