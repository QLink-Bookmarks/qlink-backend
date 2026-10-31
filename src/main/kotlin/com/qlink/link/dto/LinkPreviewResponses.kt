@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkPreviewResponse(
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val faviconUrl: String? = null,
    val isVideo: Boolean = false,
)
