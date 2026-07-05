package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.service.SendAnnouncementService
import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage
import com.qlink.post.domain.PostType
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.dto.CreatePostResponse
import com.qlink.post.repository.PostRepository
import com.qlink.user.repository.UserRepository
import org.slf4j.LoggerFactory

class CreatePostService(
    private val tx: TransactionRunner,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val sendAnnouncementService: SendAnnouncementService,
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
            // Online fan-out (in-app + device multicast). Best-effort: a delivery failure must
            // not undo the already-created post.
            runCatching { sendAnnouncementService.sendForPost(post.id!!, post.title) }
                .onFailure { log.warn("Announcement broadcast failed for post={}", post.id, it) }
        }

        return CreatePostResponse(id = post.id!!)
    }
}
