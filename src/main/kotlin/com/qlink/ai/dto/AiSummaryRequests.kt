@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.dto

import com.qlink.todo.domain.RepeatDay
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AiSummaryRequest(
    val id: Long? = null,
    val folderId: Long? = null,
    val userProviderId: Long,
    val modelId: Long,
    val url: String,
    val title: String? = null,
    val generateTodo: Boolean = false,
    val thumbnailUrl: String? = null,
    val faviconUrl: String? = null,
    val isVideo: Boolean = false,
    val memo: String? = null,
    val todos: List<AiSummaryTodoRequest>? = null,
)

@Serializable
data class AiSummaryTodoRequest(
    val title: String,
    val reminderAt: Instant? = null,
    val repeatUntil: Instant? = null,
    val repeatDays: List<RepeatDay>? = null,
    val repeatTime: String? = null,
    val repeatTimezone: String? = null,
)
