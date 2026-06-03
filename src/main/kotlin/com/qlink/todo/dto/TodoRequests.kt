@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import com.qlink.todo.domain.RepeatDay
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateTodoRequest(
    val linkId: Long,
    val title: String,
    val reminderAt: Instant? = null,
    val repeatUntil: Instant? = null,
    val repeatDays: List<RepeatDay>? = null,
    val repeatTime: String? = null,
    val repeatTimezone: String? = null,
)

@Serializable
data class UpdateTodoRequest(
    val linkId: Long,
    val title: String,
    val reminderAt: Instant? = null,
    val repeatUntil: Instant? = null,
    val repeatDays: List<RepeatDay>? = null,
    val repeatTime: String? = null,
    val repeatTimezone: String? = null,
)

@Serializable
data class CompleteTodoRequest(
    val isCompleted: Boolean,
)
