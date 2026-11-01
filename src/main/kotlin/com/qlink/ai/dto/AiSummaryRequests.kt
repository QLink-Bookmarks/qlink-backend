@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.dto

import kotlinx.serialization.Serializable

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
)
