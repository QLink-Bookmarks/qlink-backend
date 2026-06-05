package com.qlink.notification.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import com.qlink.todo.domain.Todo
import kotlin.time.Instant

private const val MAX_TITLE_LENGTH = 50
private const val MAX_MESSAGE_LENGTH = 200
private const val TODO_NOTIFICATION_MESSAGE = "할 일 알림 시간이에요"

class Notification(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val message: String,
    val context: NotificationContext,
    val contextId: Long,
    val willFireAt: Instant,
    val scheduledAt: Instant? = null,
    val firedAt: Instant? = null,
    val failedAt: Instant? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        title.isNotBlank().requireTrue(ErrorCode.NOTIFICATION_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.NOTIFICATION_TITLE_OVER_MAX)
        message.requireNotOver(MAX_MESSAGE_LENGTH, ErrorCode.NOTIFICATION_MESSAGE_OVER_MAX)
        (successCount >= 0).requireTrue(ErrorCode.NOTIFICATION_COUNT_INVALID)
        (failureCount >= 0).requireTrue(ErrorCode.NOTIFICATION_COUNT_INVALID)
    }

    val isPending: Boolean
        get() = firedAt == null && failedAt == null

    val isTodo: Boolean
        get() = context == NotificationContext.TODO

    fun markScheduled(scheduledAt: Instant): Notification =
        copy(
            scheduledAt = scheduledAt,
        )

    fun markFired(firedAt: Instant): Notification =
        copy(
            firedAt = firedAt,
            successCount = 1,
            failureCount = 0,
        )

    fun markFailed(failedAt: Instant): Notification =
        copy(
            failedAt = failedAt,
            successCount = 0,
            failureCount = 1,
        )

    fun recordSendResult(
        handledAt: Instant,
        successCount: Int,
        failureCount: Int,
    ): Notification =
        copy(
            firedAt = handledAt.takeIf { successCount > 0 },
            failedAt = handledAt.takeIf { failureCount > 0 },
            successCount = successCount,
            failureCount = failureCount,
        )

    private fun copy(
        scheduledAt: Instant? = this.scheduledAt,
        firedAt: Instant? = this.firedAt,
        failedAt: Instant? = this.failedAt,
        successCount: Int = this.successCount,
        failureCount: Int = this.failureCount,
    ): Notification =
        Notification(
            id = id,
            userId = userId,
            title = title,
            message = message,
            context = context,
            contextId = contextId,
            willFireAt = willFireAt,
            scheduledAt = scheduledAt,
            firedAt = firedAt,
            failedAt = failedAt,
            successCount = successCount,
            failureCount = failureCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun todo(todo: Todo): Notification? {
            val todoId = todo.id ?: return null
            val reminderAt = todo.reminderAt ?: return null
            if (todo.isCompleted) return null

            return Notification(
                userId = todo.ownerId,
                title = todo.title,
                message = TODO_NOTIFICATION_MESSAGE,
                context = NotificationContext.TODO,
                contextId = todoId,
                willFireAt = reminderAt,
            )
        }
    }
}
