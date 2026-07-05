package com.qlink.post.repository

import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage
import com.qlink.post.repository.table.PostImages
import com.qlink.post.repository.table.Posts
import com.qlink.post.repository.table.fromDomain
import com.qlink.post.repository.table.toPostDomain
import com.qlink.post.repository.table.toPostImageDomain
import org.jetbrains.exposed.v1.jdbc.insertReturning

class DbPostRepository : PostRepository {
    override suspend fun insert(post: Post): Post =
        Posts
            .insertReturning { it.fromDomain(post) }
            .single()
            .toPostDomain()

    override suspend fun insertImages(images: List<PostImage>): List<PostImage> =
        images.map { image ->
            PostImages
                .insertReturning { it.fromDomain(image) }
                .single()
                .toPostImageDomain()
        }
}
