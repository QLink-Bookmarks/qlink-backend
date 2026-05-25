@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.SourceType
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
