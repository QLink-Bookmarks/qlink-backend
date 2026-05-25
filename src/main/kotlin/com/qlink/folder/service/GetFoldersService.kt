package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.scroll.DEFAULT_SCROLL_SIZE
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.dto.DEFAULT_FOLDER_SEARCH_ORDER
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchCursorValue
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.GetFoldersContentResponse
import com.qlink.folder.dto.SearchFoldersQuery
import com.qlink.folder.repository.FolderRepository
import com.qlink.user.repository.UserRepository

class GetFoldersService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getFolders(
        loginId: Long,
        query: String?,
        order: String,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<GetFoldersContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_OWNER_NOT_FOUND)

            val normalizedOrder =
                FolderSearchOrder.from(order.ifBlank { DEFAULT_FOLDER_SEARCH_ORDER }) ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            val cursor = scrollRequest.cursor?.let { SearchCursorCodec.decode(it, normalizedOrder, ::validateCursorValue) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_SCROLL_SIZE
            val queries =
                folderRepository.search(
                    ownerId = loginId,
                    query = query,
                    order = normalizedOrder,
                    cursor = cursor,
                    size = size,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents =
                    contents.map { folder ->
                        GetFoldersContentResponse(
                            id = folder.id,
                            name = folder.name,
                            emoji = folder.emoji,
                            isShared = folder.sharedAt != null,
                            shareCounts = folder.shareCounts,
                            linkCounts = folder.linkCounts,
                        )
                    },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(it, normalizedOrder) },
                hasNext = hasNext,
            )
        }

    private fun validateCursorValue(
        value: FolderSearchCursorValue,
        expectedOrder: FolderSearchOrder,
    ) {
        when (expectedOrder) {
            FolderSearchOrder.LATEST, FolderSearchOrder.EARLIEST -> value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            FolderSearchOrder.LAXICO -> {
                value.name ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
                value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
            FolderSearchOrder.SIMILAR -> {
                value.score ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
                value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
        }
    }

    private fun encodeCursor(
        query: SearchFoldersQuery,
        order: FolderSearchOrder,
    ): String =
        SearchCursorCodec.encode(
            order = order,
            value =
                FolderSearchCursorValue(
                    id = query.id,
                    name = query.name,
                    score = query.score,
                ),
        )
}
