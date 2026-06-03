@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import com.qlink.link.repository.table.Links
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.repository.table.Todos
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

const val DEFAULT_TODO_SEARCH_ORDER = "latest"
const val DEFAULT_TODO_SCROLL_SIZE = 50

typealias TodoSearchOrder = SearchOrder

typealias TodoSearchCursor = SearchCursor<TodoSearchCursorValue>

@Serializable
data class TodoSearchCursorValue(
    val id: Long? = null,
)

enum class TodoReminderFilter {
    OVERDUE,
    UPCOMING,
    ;

    companion object {
        fun from(value: String): TodoReminderFilter? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class LinkDetailTodoQuery(
    val id: Long,
    val title: String,
    val completedAt: Instant?,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
)

data class LinkSearchTodoQuery(
    val linkId: Long,
    val id: Long,
    val title: String,
    val completedAt: Instant?,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
    val totalCount: Int,
)

data class SearchTodosQuery(
    val id: Long,
    val title: String,
    val completedAt: Instant?,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
    val linkId: Long,
    val linkUrl: String,
    val linkTitle: String,
)

fun ResultRow.toLinkDetailTodoQuery(): LinkDetailTodoQuery =
    LinkDetailTodoQuery(
        id = this[Todos.id],
        title = this[Todos.title],
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        reminderAt = this[Todos.reminderAt]?.toKotlinInstant(),
        repeatUntil = this[Todos.repeatUntil]?.toKotlinInstant(),
        repeatDays = this[Todos.repeatDays]?.map { RepeatDay.valueOf(it) },
        repeatTime = this[Todos.repeatTime]?.toString(),
    )

fun ResultRow.toLinkSearchTodoQuery(): LinkSearchTodoQuery =
    LinkSearchTodoQuery(
        linkId = this[Todos.linkId],
        id = this[Todos.id],
        title = this[Todos.title],
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        reminderAt = this[Todos.reminderAt]?.toKotlinInstant(),
        repeatUntil = this[Todos.repeatUntil]?.toKotlinInstant(),
        repeatDays = this[Todos.repeatDays]?.map { RepeatDay.valueOf(it) },
        repeatTime = this[Todos.repeatTime]?.toString(),
        totalCount = 0,
    )

fun ResultRow.toSearchTodosQuery(linkTitle: Expression<String>): SearchTodosQuery =
    SearchTodosQuery(
        id = this[Todos.id],
        title = this[Todos.title],
        completedAt = this[Todos.completedAt]?.toKotlinInstant(),
        reminderAt = this[Todos.reminderAt]?.toKotlinInstant(),
        repeatUntil = this[Todos.repeatUntil]?.toKotlinInstant(),
        repeatDays = this[Todos.repeatDays]?.map { RepeatDay.valueOf(it) },
        repeatTime = this[Todos.repeatTime]?.toString(),
        linkId = this[Todos.linkId],
        linkUrl = this[Links.url],
        linkTitle = this[linkTitle],
    )
