package com.qlink.support.fixture

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.todo.domain.Todo
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object NotificationFixture {
    fun createRandomNotificationOf(
        userId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        message: String = RandomFixture.randomSentenceWithMax(200),
        context: NotificationContext = NotificationContext.TODO,
        contextId: Long = RandomFixture.randomId(),
        willFireAt: Instant = Clock.System.now().plus(1.days),
        firedAt: Instant? = null,
        failedAt: Instant? = null,
        readAt: Instant? = null,
    ): Notification =
        Notification(
            userId = userId,
            title = title,
            message = message,
            context = context,
            contextId = contextId,
            willFireAt = willFireAt,
            firedAt = firedAt,
            failedAt = failedAt,
            readAt = readAt,
        )

    fun createSameScheduleNotificationOf(
        notification: Notification,
        firedAt: Instant? = null,
        failedAt: Instant? = null,
    ): Notification =
        createRandomNotificationOf(
            userId = notification.userId,
            context = notification.context,
            contextId = notification.contextId,
            willFireAt = notification.willFireAt,
            firedAt = firedAt,
            failedAt = failedAt,
        )

    fun createTodoNotificationOf(
        todo: Todo,
        willFireAt: Instant = todo.reminderAt!!,
        firedAt: Instant? = null,
        failedAt: Instant? = null,
    ): Notification =
        createRandomNotificationOf(
            userId = todo.ownerId,
            context = NotificationContext.TODO,
            contextId = todo.id!!,
            willFireAt = willFireAt,
            firedAt = firedAt,
            failedAt = failedAt,
        )
}
