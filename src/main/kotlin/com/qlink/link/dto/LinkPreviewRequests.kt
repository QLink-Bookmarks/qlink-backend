@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkPreviewRequest(
    val url: String,
)
