package com.qlink.post.repository

import com.qlink.common.search.SearchOrder
import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage
import com.qlink.post.domain.PostType
import com.qlink.post.dto.PostSearchCursor
import com.qlink.post.dto.PostSearchOrder
import com.qlink.post.dto.SearchPostsQuery
import com.qlink.post.repository.table.PostImages
import com.qlink.post.repository.table.Posts
import com.qlink.post.repository.table.fromDomain
import com.qlink.post.repository.table.toPostDomain
import com.qlink.post.repository.table.toPostImageDomain
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.toKotlinInstant

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

    override suspend fun findById(postId: Long): Post? =
        Posts
            .selectAll()
            .where { Posts.id eq postId }
            .singleOrNull()
            ?.toPostDomain()

    override suspend fun findImagesByPostId(postId: Long): List<PostImage> =
        PostImages
            .selectAll()
            .where { PostImages.postId eq postId }
            .orderBy(PostImages.id to SortOrder.ASC)
            .map { it.toPostImageDomain() }

    override suspend fun search(
        type: PostType,
        query: String?,
        order: PostSearchOrder,
        cursor: PostSearchCursor?,
        size: Int,
    ): List<SearchPostsQuery> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }

        return Posts
            .join(
                otherTable = Users,
                joinType = JoinType.INNER,
                additionalConstraint = { Posts.authorId eq Users.id },
            ).select(Posts.id, Posts.title, Users.nickname, Posts.createdAt)
            .where { Posts.type eq type }
            .apply {
                normalizedQuery?.let { keyword ->
                    andWhere { Posts.title like "%$keyword%" }
                }
                cursor?.value?.id?.let { cursorId ->
                    andWhere {
                        when (order) {
                            SearchOrder.LATEST -> Posts.id less cursorId
                            else -> Posts.id less cursorId
                        }
                    }
                }
            }.orderBy(Posts.id to SortOrder.DESC)
            .limit(size + 1)
            .map {
                SearchPostsQuery(
                    id = it[Posts.id],
                    title = it[Posts.title],
                    author = it[Users.nickname],
                    createdAt = it[Posts.createdAt].toKotlinInstant(),
                )
            }
    }
}
