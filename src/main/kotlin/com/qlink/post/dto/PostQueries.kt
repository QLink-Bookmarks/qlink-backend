@file:Suppress("ktlint:standard:filename")

package com.qlink.post.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import com.qlink.post.domain.PostType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val DEFAULT_POST_SEARCH_ORDER = "latest"
const val DEFAULT_POST_SCROLL_SIZE = 15
const val DEFAULT_POST_TYPE = "ANNOUNCEMENT"

typealias PostSearchOrder = SearchOrder

typealias PostSearchCursor = SearchCursor<PostSearchCursorValue>

@Serializable
data class PostSearchCursorValue(
    val id: Long? = null,
    val type: PostType? = null,
    val title: String? = null,
)

data class SearchPostsQuery(
    val id: Long,
    val title: String,
    val author: String,
    val createdAt: Instant,
)
