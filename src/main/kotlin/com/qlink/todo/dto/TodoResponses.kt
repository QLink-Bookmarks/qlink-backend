@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

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
)

@Serializable
data class CompleteTodoResponse(
    val completeAt: Instant?,
)
