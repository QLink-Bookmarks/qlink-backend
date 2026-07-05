package com.qlink.post.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import kotlin.time.Instant

private const val MAX_TITLE_LENGTH = 100

class Post(
    val id: Long? = null,
    val title: String,
    val contents: String,
    val type: PostType,
    val authorId: Long,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        title.isNotBlank().requireTrue(ErrorCode.POST_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.POST_TITLE_OVER_MAX)
        contents.isNotBlank().requireTrue(ErrorCode.POST_CONTENTS_BLANK)
    }

    val isAnnouncement: Boolean
        get() = type == PostType.ANNOUNCEMENT
}
