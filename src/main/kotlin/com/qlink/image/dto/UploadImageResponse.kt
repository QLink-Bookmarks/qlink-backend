package com.qlink.image.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadImageResponse(
    val url: String,
)
