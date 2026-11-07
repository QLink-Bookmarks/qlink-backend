package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.common.search.SearchOrder
import com.qlink.common.search.SortDirection

class LinkSort private constructor(
    val orderKey: LinkOrderKey,
    val direction: SortDirection,
) {
    fun validateCursor(
        orderKey: LinkOrderKey?,
        direction: SortDirection?,
        order: SearchOrder?,
    ) {
        val cursorSort =
            if (orderKey != null && direction != null) {
                LinkSort(orderKey = orderKey, direction = direction)
            } else {
                order?.let { legacyOf(it) }
            }

        (cursorSort != null && cursorSort.orderKey == this.orderKey && cursorSort.direction == this.direction)
            .requireTrue(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH)
    }

    companion object {
        private val DEFAULT = LinkSort(orderKey = LinkOrderKey.CREATED_AT, direction = SortDirection.DESC)

        fun of(
            orderKey: String?,
            order: String?,
        ): LinkSort {
            val requestedKey = orderKey?.takeIf { it.isNotBlank() }
            val requestedOrder = order?.takeIf { it.isNotBlank() }

            return when {
                requestedKey != null -> ofKey(orderKey = requestedKey, order = requestedOrder)
                requestedOrder != null -> ofOrder(requestedOrder)
                else -> DEFAULT
            }
        }

        private fun ofKey(
            orderKey: String,
            order: String?,
        ): LinkSort {
            val key = LinkOrderKey.from(orderKey) ?: throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)
            val direction =
                order
                    ?.let { SortDirection.from(it) ?: throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER) }
                    ?: key.defaultDirection
            (direction in key.directions).requireTrue(ErrorCode.COMMON_INVALID_SORT_ORDER)

            return LinkSort(orderKey = key, direction = direction)
        }

        private fun ofOrder(order: String): LinkSort =
            SortDirection.from(order)?.let { LinkSort(orderKey = LinkOrderKey.CREATED_AT, direction = it) }
                ?: SearchOrder.from(order)?.let { legacyOf(it) }
                ?: throw BusinessException(ErrorCode.COMMON_INVALID_SORT_ORDER)

        private fun legacyOf(order: SearchOrder): LinkSort =
            when (order) {
                SearchOrder.LATEST -> DEFAULT
                SearchOrder.EARLIEST -> LinkSort(orderKey = LinkOrderKey.CREATED_AT, direction = SortDirection.ASC)
                SearchOrder.LAXICO -> LinkSort(orderKey = LinkOrderKey.TITLE, direction = SortDirection.ASC)
                SearchOrder.SIMILAR -> LinkSort(orderKey = LinkOrderKey.SIMILARITY, direction = SortDirection.DESC)
            }
    }
}
