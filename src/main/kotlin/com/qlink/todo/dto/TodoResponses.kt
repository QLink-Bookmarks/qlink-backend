@file:Suppress("ktlint:standard:filename")

package com.qlink.todo.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTodoResponse(
    val id: Long,
)
