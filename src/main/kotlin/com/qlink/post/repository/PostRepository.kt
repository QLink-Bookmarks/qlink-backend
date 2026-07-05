package com.qlink.post.repository

import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage

interface PostRepository {
    suspend fun insert(post: Post): Post

    suspend fun insertImages(images: List<PostImage>): List<PostImage>
}
