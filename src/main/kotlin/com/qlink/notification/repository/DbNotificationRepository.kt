package com.qlink.notification.repository

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.dto.NotificationSearchCursor
import com.qlink.notification.dto.NotificationSearchOrder
import com.qlink.notification.dto.SearchNotificationsQuery
import com.qlink.notification.repository.table.Notifications
import com.qlink.notification.repository.table.fromDomain
import com.qlink.notification.repository.table.refreshNotificationUpdatedAt
import com.qlink.notification.repository.table.toNotificationDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class DbNotificationRepository : NotificationRepository {
    override suspend fun insert(notification: Notification): Notification =
        Notifications
            .insertReturning { it.fromDomain(notification) }
            .single()
            .toNotificationDomain()

    override suspend fun findById(notificationId: Long): Notification? =
        Notifications
            .selectAll()
            .where { Notifications.id eq notificationId }
            .singleOrNull()
            ?.toNotificationDomain()

    override suspend fun findPendingById(notificationId: Long): Notification? =
        Notifications
            .selectAll()
            .where {
                (Notifications.id eq notificationId) and
                    Notifications.firedAt.isNull() and
                    Notifications.failedAt.isNull()
            }.singleOrNull()
            ?.toNotificationDomain()

    override suspend fun findPendingBetween(
        startInclusive: Instant,
        endExclusive: Instant,
    ): List<Notification> =
        Notifications
            .selectAll()
            .where {
                (Notifications.willFireAt greaterEq startInclusive.toJavaInstant()) and
                    (Notifications.willFireAt less endExclusive.toJavaInstant()) and
                    Notifications.firedAt.isNull() and
                    Notifications.failedAt.isNull()
            }.map { it.toNotificationDomain() }

    override suspend fun findByContext(
        context: NotificationContext,
        contextId: Long,
    ): List<Notification> =
        Notifications
            .selectAll()
            .where {
                (Notifications.context eq context) and
                    (Notifications.contextId eq contextId)
            }.map { it.toNotificationDomain() }

    override suspend fun findPendingByContext(
        context: NotificationContext,
        contextId: Long,
    ): List<Notification> =
        Notifications
            .selectAll()
            .where {
                (Notifications.context eq context) and
                    (Notifications.contextId eq contextId) and
                    Notifications.firedAt.isNull() and
                    Notifications.failedAt.isNull()
            }.map { it.toNotificationDomain() }

    override suspend fun search(
        userId: Long,
        query: String?,
        order: NotificationSearchOrder,
        cursor: NotificationSearchCursor?,
        size: Int,
    ): List<SearchNotificationsQuery> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }

        return Notifications
            .select(
                Notifications.id,
                Notifications.title,
                Notifications.message,
                Notifications.firedAt,
                Notifications.readAt,
                Notifications.context,
                Notifications.contextId,
            ).where {
                (Notifications.userId eq userId) and Notifications.firedAt.isNotNull()
            }.apply {
                normalizedQuery?.let { keyword ->
                    andWhere {
                        (Notifications.title like "%$keyword%") or
                            (Notifications.message like "%$keyword%")
                    }
                }
                cursor?.value?.id?.let { cursorId ->
                    andWhere {
                        when (order) {
                            com.qlink.common.search.SearchOrder.LATEST -> Notifications.id less cursorId
                            else -> Notifications.id less cursorId
                        }
                    }
                }
            }.orderBy(Notifications.id to SortOrder.DESC)
            .limit(size + 1)
            .map {
                SearchNotificationsQuery(
                    id = it[Notifications.id],
                    title = it[Notifications.title],
                    message = it[Notifications.message],
                    firedAt = it[Notifications.firedAt]!!.toKotlinInstant(),
                    readAt = it[Notifications.readAt]?.toKotlinInstant(),
                    context = it[Notifications.context],
                    contextId = it[Notifications.contextId],
                )
            }
    }

    override suspend fun countUnread(userId: Long): Int =
        Notifications
            .select(Notifications.id.count())
            .where {
                (Notifications.userId eq userId) and
                    Notifications.firedAt.isNotNull() and
                    Notifications.readAt.isNull()
            }.single()[Notifications.id.count()]
            .toInt()

    override suspend fun update(notification: Notification): Notification =
        Notifications
            .updateReturning(where = { Notifications.id eq notification.id!! }) {
                it.fromDomain(notification)
                it.refreshNotificationUpdatedAt()
            }.single()
            .toNotificationDomain()

    override suspend fun deletePendingByContext(
        context: NotificationContext,
        contextId: Long,
    ): List<Long> {
        val ids =
            Notifications
                .select(Notifications.id)
                .where {
                    (Notifications.context eq context) and
                        (Notifications.contextId eq contextId) and
                        Notifications.firedAt.isNull() and
                        Notifications.failedAt.isNull()
                }.map { it[Notifications.id] }

        if (ids.isNotEmpty()) {
            Notifications.deleteWhere { Notifications.id inList ids }
        }

        return ids
    }
}
