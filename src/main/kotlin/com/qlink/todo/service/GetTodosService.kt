package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireTrue
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.todo.domain.TodoOrderKey
import com.qlink.todo.domain.TodoSort
import com.qlink.todo.domain.TodoStatus
import com.qlink.todo.dto.DEFAULT_TODO_SCROLL_SIZE
import com.qlink.todo.dto.GetTodosContentResponse
import com.qlink.todo.dto.SearchTodosQuery
import com.qlink.todo.dto.TodoReminderFilter
import com.qlink.todo.dto.TodoSearchCursor
import com.qlink.todo.dto.TodoSearchCursorValue
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class GetTodosService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getTodos(
        loginId: Long,
        status: String? = null,
        orderKey: String? = null,
        order: String?,
        scrollRequest: ScrollRequest,
        isCompleted: Boolean?,
        reminderAt: String?,
    ): ScrollResponse<GetTodosContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val sort = TodoSort.of(orderKey = orderKey, order = order)
            val cursor = scrollRequest.cursor?.let { decodeCursor(encoded = it, sort = sort) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_TODO_SCROLL_SIZE
            val statusFilter = status?.let { parseStatus(status = it, isCompleted = isCompleted, reminderAt = reminderAt) }
            val reminderFilter = reminderAt?.let { TodoReminderFilter.from(it) ?: throw BusinessException(ErrorCode.COMMON_INVALID_FILTER) }
            val now = Clock.System.now()
            val queries =
                todoRepository.search(
                    ownerId = loginId,
                    sort = sort,
                    cursor = cursor,
                    size = size,
                    status = statusFilter,
                    isCompleted = isCompleted,
                    reminderAt = reminderFilter,
                    now = now,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)
            val statusById = todoRepository.findAllByIds(contents.map { it.id }).associate { it.id!! to it.status(now) }

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents = contents.map { it.toResponse(statusById.getValue(it.id)) },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(query = it, sort = sort) },
                hasNext = hasNext,
            )
        }

    private fun parseStatus(
        status: String,
        isCompleted: Boolean?,
        reminderAt: String?,
    ): TodoStatus {
        (isCompleted == null && reminderAt == null).requireTrue(ErrorCode.COMMON_INVALID_FILTER)

        return TodoStatus.from(status) ?: throw BusinessException(ErrorCode.COMMON_INVALID_FILTER)
    }

    private suspend fun decodeCursor(
        encoded: String,
        sort: TodoSort,
    ): TodoSearchCursor {
        val cursor = Base64CursorCodec.decode<TodoSearchCursor>(encoded)
        sort.validateCursor(orderKey = cursor.orderKey, direction = cursor.direction, order = cursor.order)
        val id = cursor.value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)

        if (sort.orderKey != TodoOrderKey.CREATED_AT || cursor.value.createdAt != null) {
            return cursor
        }

        val createdAt = todoRepository.findCreatedAtById(id) ?: throw BusinessException(ErrorCode.COMMON_CURSOR_MALFORMED)

        return cursor.copy(value = cursor.value.copy(createdAt = createdAt))
    }

    private fun encodeCursor(
        query: SearchTodosQuery,
        sort: TodoSort,
    ): String =
        Base64CursorCodec.encode(
            TodoSearchCursor(
                orderKey = sort.orderKey,
                direction = sort.direction,
                value =
                    TodoSearchCursorValue(
                        id = query.id,
                        createdAt = query.createdAt,
                        reminderAt = query.reminderAt,
                        completedAt = query.completedAt,
                    ),
            ),
        )

    private fun SearchTodosQuery.toResponse(status: TodoStatus): GetTodosContentResponse =
        GetTodosContentResponse(
            id = id,
            title = title,
            status = status,
            completedAt = completedAt,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            createdAt = createdAt,
            linkId = linkId,
            linkUrl = linkUrl,
            linkTitle = linkTitle,
            linkThumbnailUrl = linkThumbnailUrl,
            linkFaviconUrl = linkFaviconUrl,
        )
}
