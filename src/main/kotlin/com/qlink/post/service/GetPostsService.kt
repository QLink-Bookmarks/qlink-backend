package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.search.SearchOrder
import com.qlink.common.transaction.TransactionRunner
import com.qlink.post.domain.PostType
import com.qlink.post.dto.DEFAULT_POST_SCROLL_SIZE
import com.qlink.post.dto.DEFAULT_POST_SEARCH_ORDER
import com.qlink.post.dto.DEFAULT_POST_TYPE
import com.qlink.post.dto.GetPostsContentResponse
import com.qlink.post.dto.PostSearchCursorValue
import com.qlink.post.dto.PostSearchOrder
import com.qlink.post.dto.SearchPostsQuery
import com.qlink.post.repository.PostRepository

class GetPostsService(
    private val tx: TransactionRunner,
    private val postRepository: PostRepository,
) {
    suspend fun getPosts(
        role: Role,
        type: String?,
        query: String?,
        order: String,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<GetPostsContentResponse> =
        tx.readOnly {
            val postType = PostType.from(type ?: DEFAULT_POST_TYPE)
            postType.validateReadable(role.isAdmin)

            val normalizedOrder = normalizeOrder(order)
            val cursor = scrollRequest.cursor?.let { SearchCursorCodec.decode(it, normalizedOrder, ::validateCursorValue) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_POST_SCROLL_SIZE
            val queries =
                postRepository.search(
                    type = postType,
                    query = query,
                    order = normalizedOrder,
                    cursor = cursor,
                    size = size,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents = contents.map { it.toResponse() },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(it, postType, normalizedOrder) },
                hasNext = hasNext,
            )
        }

    private fun normalizeOrder(order: String): PostSearchOrder {
        val normalizedOrder =
            SearchOrder.from(order.ifBlank { DEFAULT_POST_SEARCH_ORDER })
                ?: throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)

        if (normalizedOrder != SearchOrder.LATEST) {
            throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)
        }

        return normalizedOrder
    }

    private fun validateCursorValue(
        value: PostSearchCursorValue,
        expectedOrder: PostSearchOrder,
    ) {
        when (expectedOrder) {
            SearchOrder.LATEST -> value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            else -> throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
        }
    }

    private fun encodeCursor(
        query: SearchPostsQuery,
        type: PostType,
        order: PostSearchOrder,
    ): String =
        SearchCursorCodec.encode(
            order = order,
            value = PostSearchCursorValue(id = query.id, type = type, title = query.title),
        )

    private fun SearchPostsQuery.toResponse(): GetPostsContentResponse =
        GetPostsContentResponse(
            id = id,
            title = title,
            author = author,
            createdAt = createdAt,
        )
}
