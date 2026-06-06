package com.qlink.notification.repository

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.table.Notifications
import com.qlink.notification.repository.table.fromDomain
import com.qlink.notification.repository.table.refreshNotificationUpdatedAt
import com.qlink.notification.repository.table.toNotificationDomain
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
