@file:Suppress("ktlint:standard:filename")

package com.qlink.post.dto

import com.qlink.post.domain.PostType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreatePostResponse(
    val id: Long,
)

@Serializable
data class GetPostResponse(
    val id: Long,
    val title: String,
    val contents: String,
    val type: PostType,
    val authorId: Long,
    val author: String,
    val imageUrls: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class GetPostsContentResponse(
    val id: Long,
    val title: String,
    val author: String,
    val createdAt: Instant,
)
