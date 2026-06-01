package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.scroll.DEFAULT_SCROLL_SIZE
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.dto.DEFAULT_LINK_SEARCH_ORDER
import com.qlink.link.dto.GetLinksContentResponse
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.LinkSearchTodoResponse
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.dto.LinkSearchTodoQuery
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class GetLinksService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getLinks(
        loginId: Long,
        query: String?,
        folderId: Long?,
        order: String,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<GetLinksContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val normalizedOrder =
                LinkSearchOrder.from(order.ifBlank { DEFAULT_LINK_SEARCH_ORDER }) ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            val cursor = scrollRequest.cursor?.let { SearchCursorCodec.decode(it, normalizedOrder, ::validateCursorValue) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_SCROLL_SIZE
            val queries =
                linkRepository.search(
                    ownerId = loginId,
                    query = query,
                    folderId = folderId,
                    order = normalizedOrder,
                    cursor = cursor,
                    size = size,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)
            val todosByLinkId =
                todoRepository
                    .findAllByLinkIdsForLinkSearch(contents.map { it.id })
                    .groupBy { it.linkId }

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents =
                    contents.map { queryRow ->
                        val todoQueries = todosByLinkId[queryRow.id].orEmpty()

                        GetLinksContentResponse(
                            id = queryRow.id,
                            folderId = queryRow.folderId,
                            folderName = queryRow.folderName,
                            folderEmoji = queryRow.folderEmoji,
                            workModel = queryRow.workModel,
                            url = queryRow.url,
                            title = queryRow.title,
                            tags = queryRow.tags,
                            createdAt = queryRow.createdAt,
                            todos = todoQueries.map { it.toResponse() },
                            countMoreTodos = (todoQueries.firstOrNull()?.totalCount ?: 0) - todoQueries.size,
                        )
                    },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(it, normalizedOrder) },
                hasNext = hasNext,
            )
        }

    private fun validateCursorValue(
        value: LinkSearchCursorValue,
        expectedOrder: LinkSearchOrder,
    ) {
        when (expectedOrder) {
            LinkSearchOrder.LATEST, LinkSearchOrder.EARLIEST -> {
                value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }

            LinkSearchOrder.LAXICO -> {
                value.title ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
                value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }

            LinkSearchOrder.SIMILAR -> {
                value.score ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
                value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
        }
    }

    private fun encodeCursor(
        query: SearchLinksQuery,
        order: LinkSearchOrder,
    ): String =
        SearchCursorCodec.encode(
            order = order,
            value =
                LinkSearchCursorValue(
                    id = query.id,
                    title = query.title,
                    score = query.score,
                    titleScore = query.titleScore,
                    urlScore = query.urlScore,
                    tagsScore = query.tagsScore,
                    summaryScore = query.summaryScore,
                    memoScore = query.memoScore,
                ),
        )

    private fun LinkSearchTodoQuery.toResponse(): LinkSearchTodoResponse =
        LinkSearchTodoResponse(
            id = id,
            title = title,
            completedAt = completedAt,
            reminderAt = reminderAt,
        )
}
