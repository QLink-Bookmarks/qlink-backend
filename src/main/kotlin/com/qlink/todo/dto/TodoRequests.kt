@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateTodoRequest(
    val linkId: Long,
    val title: String,
    val reminderAt: Instant? = null,
)

@Serializable
data class UpdateTodoRequest(
    val linkId: Long,
    val title: String,
    val reminderAt: Instant? = null,
)
