package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.search.SearchOrder
import com.qlink.common.transaction.TransactionRunner
import com.qlink.todo.dto.DEFAULT_TODO_SCROLL_SIZE
import com.qlink.todo.dto.DEFAULT_TODO_SEARCH_ORDER
import com.qlink.todo.dto.GetTodosContentResponse
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursorValue
import com.qlink.todo.dto.TodoSearchOrder
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class GetTodosService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getTodos(
        loginId: Long,
        order: String,
        scrollRequest: ScrollRequest,
        isCompleted: Boolean?,
        reminderAt: String?,
    ): ScrollResponse<GetTodosContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val normalizedOrder = normalizeOrder(order)
            val cursor = scrollRequest.cursor?.let { SearchCursorCodec.decode(it, normalizedOrder, ::validateCursorValue) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_TODO_SCROLL_SIZE
            val reminderFilter = reminderAt?.let { TodoReminderFilter.from(it) ?: throw BusinessException(ErrorCode.COMMON_INVALID_FILTER) }
            val queries =
                todoRepository.search(
                    ownerId = loginId,
                    order = normalizedOrder,
                    cursor = cursor,
                    size = size,
                    isCompleted = isCompleted,
                    reminderAt = reminderFilter,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents = contents.map { it.toResponse() },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(it, normalizedOrder) },
                hasNext = hasNext,
            )
        }

    private fun normalizeOrder(order: String): TodoSearchOrder {
        val normalizedOrder =
            SearchOrder.from(order.ifBlank { DEFAULT_TODO_SEARCH_ORDER })
                ?: throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)

        if (normalizedOrder != SearchOrder.LATEST) {
            throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)
        }

        return normalizedOrder
    }

    private fun validateCursorValue(
        value: TodoSearchCursorValue,
        expectedOrder: TodoSearchOrder,
    ) {
        when (expectedOrder) {
            SearchOrder.LATEST -> value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            else -> throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
        }
    }

    private fun encodeCursor(
        query: SearchTodosQuery,
        order: TodoSearchOrder,
    ): String =
        SearchCursorCodec.encode(
            order = order,
            value = TodoSearchCursorValue(id = query.id),
        )

    private fun SearchTodosQuery.toResponse(): GetTodosContentResponse =
        GetTodosContentResponse(
            id = id,
            title = title,
            completedAt = completedAt,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            linkId = linkId,
            linkUrl = linkUrl,
            linkTitle = linkTitle,
        )
}
