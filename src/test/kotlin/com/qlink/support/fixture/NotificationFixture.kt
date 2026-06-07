package com.qlink.support.fixture

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object NotificationFixture {
    fun createRandomNotificationOf(
        userId: Long,
        title: String = RandomFixture.randomSentenceWithMax(50),
        message: String = RandomFixture.randomSentenceWithMax(200),
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
            context = NotificationContext.TODO,
            contextId = contextId,
            willFireAt = willFireAt,
            firedAt = firedAt,
            failedAt = failedAt,
            readAt = readAt,
        )
}
