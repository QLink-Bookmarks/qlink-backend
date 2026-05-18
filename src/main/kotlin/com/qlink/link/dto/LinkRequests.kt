@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.link.domain.SourceType
import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkRequest(
    val folderId: Long? = null,
    val url: String,
    val title: String,
    val summary: String? = null,
    val tags: List<String>,
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
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
