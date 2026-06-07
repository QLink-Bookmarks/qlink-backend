package com.qlink.notification.repository

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.dto.NotificationSearchCursor
import com.qlink.notification.dto.NotificationSearchOrder
import com.qlink.notification.dto.SearchNotificationsQuery
import kotlin.time.Instant

interface NotificationRepository {
    suspend fun insert(notification: Notification): Notification

    suspend fun findById(notificationId: Long): Notification?

    suspend fun findPendingById(notificationId: Long): Notification?

    suspend fun findPendingBetween(
        startInclusive: Instant,
        endExclusive: Instant,
    ): List<Notification>

    suspend fun findPendingByContext(
        context: NotificationContext,
        contextId: Long,
    ): List<Notification>

    suspend fun search(
        userId: Long,
        query: String?,
        order: NotificationSearchOrder,
        cursor: NotificationSearchCursor?,
        size: Int,
    ): List<SearchNotificationsQuery>

    suspend fun countUnread(userId: Long): Int

    suspend fun update(notification: Notification): Notification

    suspend fun deletePendingByContext(
        context: NotificationContext,
        contextId: Long,
    ): List<Long>
}
