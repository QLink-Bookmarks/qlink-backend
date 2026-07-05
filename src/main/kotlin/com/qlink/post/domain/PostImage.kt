package com.qlink.post.domain

import kotlin.time.Instant

class PostImage(
    val id: Long? = null,
    val postId: Long,
    val url: String,
    val createdAt: Instant? = null,
)
