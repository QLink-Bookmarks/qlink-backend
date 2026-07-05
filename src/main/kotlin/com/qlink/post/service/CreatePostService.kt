package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.domain.Notification
import com.qlink.notification.repository.NotificationRepository
import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage
import com.qlink.post.domain.PostType
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.dto.CreatePostResponse
import com.qlink.post.repository.PostRepository
import com.qlink.user.repository.UserRepository
import org.slf4j.LoggerFactory
import kotlin.time.Clock

class CreatePostService(
    private val tx: TransactionRunner,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(CreatePostService::class.java)

    suspend fun createPost(
        loginId: Long,
        role: Role,
        request: CreatePostRequest,
    ): CreatePostResponse {
        val type = PostType.from(request.type)
        type.validateManageable(role)

        val post =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.POST_AUTHOR_NOT_FOUND)

                val saved =
                    postRepository.insert(
                        Post(
                            title = request.title,
                            contents = request.contents,
                            type = type,
                            authorId = loginId,
                        ),
                    )

                if (request.imageUrls.isNotEmpty()) {
                    postRepository.insertImages(request.imageUrls.map { PostImage(postId = saved.id!!, url = it) })
                }

                saved
            }

        if (post.isAnnouncement) {
            // Best-effort broadcast: a delivery failure must not undo the already-created post.
            runCatching { broadcastAnnouncement(post) }
                .onFailure { log.warn("Announcement broadcast failed for post={}", post.id, it) }
        }

        return CreatePostResponse(id = post.id!!)
    }

    private suspend fun broadcastAnnouncement(post: Post) {
        val at = Clock.System.now()
        tx.required {
            userRepository.findAllIds().forEach { userId ->
                notificationRepository.insert(
                    Notification
                        .announce(userId = userId, postId = post.id!!, postTitle = post.title, at = at)
                        .markFired(at),
                )
            }
        }
    }
}
