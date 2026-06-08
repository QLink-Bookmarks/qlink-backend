package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.search.SearchCursorCodec
import com.qlink.common.search.SearchOrder
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.dto.DEFAULT_NOTIFICATION_SCROLL_SIZE
import com.qlink.notification.dto.DEFAULT_NOTIFICATION_SEARCH_ORDER
import com.qlink.notification.dto.GetNotificationsContentResponse
import com.qlink.notification.dto.NotificationSearchCursorValue
import com.qlink.notification.dto.NotificationSearchOrder
import com.qlink.notification.dto.SearchNotificationsQuery
import com.qlink.notification.repository.NotificationRepository
import com.qlink.user.repository.UserRepository

class GetNotificationsService(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getNotifications(
        loginId: Long,
        query: String?,
        order: String,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<GetNotificationsContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)

            val normalizedOrder = normalizeOrder(order)
            val cursor = scrollRequest.cursor?.let { SearchCursorCodec.decode(it, normalizedOrder, ::validateCursorValue) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_NOTIFICATION_SCROLL_SIZE
            val queries =
                notificationRepository.search(
                    userId = loginId,
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
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let { encodeCursor(it, normalizedOrder) },
                hasNext = hasNext,
            )
        }

    private fun normalizeOrder(order: String): NotificationSearchOrder {
        val normalizedOrder =
            SearchOrder.from(order.ifBlank { DEFAULT_NOTIFICATION_SEARCH_ORDER })
                ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)

        if (normalizedOrder != SearchOrder.LATEST) {
            throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
        }

        return normalizedOrder
    }

    private fun validateCursorValue(
        value: NotificationSearchCursorValue,
        expectedOrder: NotificationSearchOrder,
    ) {
        when (expectedOrder) {
            SearchOrder.LATEST -> value.id ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            else -> throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
        }
    }

    private fun encodeCursor(
        query: SearchNotificationsQuery,
        order: NotificationSearchOrder,
    ): String =
        SearchCursorCodec.encode(
            order = order,
            value = NotificationSearchCursorValue(id = query.id),
        )

    private fun SearchNotificationsQuery.toResponse(): GetNotificationsContentResponse =
        GetNotificationsContentResponse(
            id = id,
            title = title,
            message = message,
            firedAt = firedAt,
            readAt = readAt,
            context = context,
            contextId = contextId,
        )
}
