@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SourceType
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
    val thumbnailUrl: String?,
    val faviconUrl: String?,
    val isVideo: Boolean,
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
    val thumbnailUrl: String?,
    val faviconUrl: String?,
    val isVideo: Boolean,
    val status: LinkStatus,
    val tags: List<String>,
    val createdAt: Instant,
    val workModel: String?,
    val todoCounts: TodoCountsResponse,
    val isFavorite: Boolean,
)

@Serializable
data class TodoCountsResponse(
    val open: Int,
    val dueSoon: Int,
    val overdue: Int,
    val completed: Int,
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
