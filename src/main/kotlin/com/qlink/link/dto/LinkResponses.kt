@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SourceType
import com.qlink.todo.domain.RepeatDay
import com.qlink.todo.dto.LinkDetailTodoQuery
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateLinkResponse(
    val id: Long,
)

@Serializable
data class CopyLinkResponse(
    val id: Long,
)

@Serializable
data class GetLinkDetailResponse(
    val id: Long,
    val url: String,
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val memo: String?,
    val sourceType: SourceType,
    val status: LinkStatus,
    val createdAt: Instant,
    val folderId: Long?,
    val folderName: String?,
    val folderEmoji: String?,
    val todos: List<LinkDetailTodoQuery>,
    val workModel: String?,
    val isFavorite: Boolean,
)

@Serializable
data class GetLinksContentResponse(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val folderEmoji: String?,
    val url: String,
    val title: String,
    val status: LinkStatus,
    val tags: List<String>,
    val createdAt: Instant,
    val workModel: String?,
    val todos: List<LinkSearchTodoResponse>,
    val countMoreTodos: Int,
    val isFavorite: Boolean,
)

@Serializable
data class LinkSearchTodoResponse(
    val id: Long,
    val title: String,
    val completedAt: Instant?,
    val reminderAt: Instant?,
    val repeatUntil: Instant?,
    val repeatDays: List<RepeatDay>?,
    val repeatTime: String?,
)

@Serializable
data class UpdateLinkResponse(
    val folderId: Long?,
    val url: String,
    val title: String,
    val summary: String?,
    val memo: String?,
    val tags: List<String>,
    val thumbnailUrl: String?,
    val sourceType: SourceType,
)
