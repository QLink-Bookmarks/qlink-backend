@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.SourceType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateLinkResponse(
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
    val createdAt: Instant,
    val folderId: Long?,
    val folderName: String?,
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
