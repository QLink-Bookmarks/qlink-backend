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
    val tags: List<String>,
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
    val reminderAt: Instant? = null,
)