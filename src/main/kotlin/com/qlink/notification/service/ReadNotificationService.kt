package com.qlink.notification.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.repository.NotificationRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class ReadNotificationService(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
) {
    suspend fun readNotification(
        loginId: Long,
        notificationId: Long,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)

            val notification = notificationRepository.findById(notificationId) ?: return@required

            val readNotification = notification.markRead(
                loginId = loginId,
                readAt = Clock.System.now(),
            )
            if (readNotification !== notification) {
                notificationRepository.update(readNotification)
            }
        }
    }
}
