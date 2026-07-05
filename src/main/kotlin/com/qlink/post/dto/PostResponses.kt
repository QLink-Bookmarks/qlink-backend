@file:Suppress("ktlint:standard:filename")

package com.qlink.post.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreatePostResponse(
    val id: Long,
)
