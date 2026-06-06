package com.qlink.support.fixture

import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

object NotificationFixture {
    fun createRandomNotificationOf(userId: Long): Notification =
        Notification(
            userId = userId,
            title = RandomFixture.randomSentenceWithMax(50),
            message = RandomFixture.randomSentenceWithMax(200),
            context = NotificationContext.TODO,
            contextId = RandomFixture.randomId(),
            willFireAt = Clock.System.now().plus(1.days),
        )
}
