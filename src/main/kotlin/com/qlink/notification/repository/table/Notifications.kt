package com.qlink.notification.repository.table

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 50)
    val message = varchar("message", 200)
    val context = enumerationByName<NotificationContext>("context", 20)
    val contextId = long("context_id")
    val willFireAt = timestamp("will_fire_at")
    val scheduledAt = timestamp("scheduled_at").nullable()
    val firedAt = timestamp("fired_at").nullable()
    val failedAt = timestamp("failed_at").nullable()
    val successCount = integer("success_count").default(0)
    val failureCount = integer("failure_count").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("notifications_context_will_fire_unique", context, contextId, willFireAt)
        index("notifications_user_id_idx", false, userId)
        index("notifications_will_fire_at_idx", false, willFireAt)
        index("notifications_context_idx", false, context, contextId)
    }
}

fun ResultRow.toNotificationDomain(): Notification =
    Notification(
        id = this[Notifications.id],
        userId = this[Notifications.userId],
        title = this[Notifications.title],
        message = this[Notifications.message],
        context = this[Notifications.context],
        contextId = this[Notifications.contextId],
        willFireAt = this[Notifications.willFireAt].toKotlinInstant(),
        scheduledAt = this[Notifications.scheduledAt]?.toKotlinInstant(),
        firedAt = this[Notifications.firedAt]?.toKotlinInstant(),
        failedAt = this[Notifications.failedAt]?.toKotlinInstant(),
        successCount = this[Notifications.successCount],
        failureCount = this[Notifications.failureCount],
        createdAt = this[Notifications.createdAt].toKotlinInstant(),
        updatedAt = this[Notifications.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(notification: Notification) {
    this[Notifications.userId] = notification.userId
    this[Notifications.title] = notification.title
    this[Notifications.message] = notification.message
    this[Notifications.context] = notification.context
    this[Notifications.contextId] = notification.contextId
    this[Notifications.willFireAt] = notification.willFireAt.toJavaInstant()
    this[Notifications.scheduledAt] = notification.scheduledAt?.toJavaInstant()
    this[Notifications.firedAt] = notification.firedAt?.toJavaInstant()
    this[Notifications.failedAt] = notification.failedAt?.toJavaInstant()
    this[Notifications.successCount] = notification.successCount
    this[Notifications.failureCount] = notification.failureCount
}

fun UpdateBuilder<*>.refreshNotificationUpdatedAt() {
    this[Notifications.updatedAt] = Clock.System.now().toJavaInstant()
}
