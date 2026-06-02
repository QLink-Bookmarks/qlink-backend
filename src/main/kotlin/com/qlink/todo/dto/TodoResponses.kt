@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import com.qlink.todo.domain.RepeatDay
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateTodoResponse(
    val id: Long,
)

@Serializable
data class UpdateTodoResponse(
    val linkId: Long,
    val title: String,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
)

@Serializable
data class CompleteTodoResponse(
    val completeAt: Instant?,
)

@Serializable
data class GetTodosContentResponse(
    val id: Long,
    val title: String,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
    val linkId: Long,
    val linkUrl: String,
    val linkTitle: String,
)
