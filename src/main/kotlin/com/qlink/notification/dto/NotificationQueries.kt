@file:Suppress("ktlint:standard:filename")

package com.qlink.notification.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import com.qlink.notification.domain.NotificationContext
import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val DEFAULT_NOTIFICATION_SEARCH_ORDER = "latest"
const val DEFAULT_NOTIFICATION_SCROLL_SIZE = 30

typealias NotificationSearchOrder = SearchOrder

typealias NotificationSearchCursor = SearchCursor<NotificationSearchCursorValue>

@Serializable
data class NotificationSearchCursorValue(
    val id: Long? = null,
)

data class SearchNotificationsQuery(
    val id: Long,
    val title: String,
    val message: String,
    val firedAt: Instant,
    val readAt: Instant?,
    val context: NotificationContext,
    val contextId: Long,
)
