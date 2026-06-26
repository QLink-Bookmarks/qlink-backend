@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.SourceType
import com.qlink.todo.domain.RepeatDay
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateLinkRequest(
    val folderId: Long? = null,
    val url: String,
    val title: String,
    val summary: String? = null,
    val memo: String? = null,
    val tags: List<String>,
    val todos: List<CreateLinkTodoRequest> = emptyList(),
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
)

@Serializable
data class CreateLinkTodoRequest(
    val title: String,
    val reminderAt: Instant? = null,
    val repeatUntil: Instant? = null,
    val repeatDays: List<RepeatDay>? = null,
    val repeatTime: String? = null,
    val repeatTimezone: String? = null,
)

@Serializable
data class UpdateLinkRequest(
    val folderId: Long? = null,
    val url: String,
    val title: String,
    val summary: String? = null,
    val memo: String? = null,
    val tags: List<String>,
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
)

@Serializable
data class SetLinkFavoriteRequest(
    val isFavorite: Boolean? = null,
)

@Serializable
data class CopyLinkRequest(
    val fromFolderId: Long,
    val toFolderId: Long,
)
