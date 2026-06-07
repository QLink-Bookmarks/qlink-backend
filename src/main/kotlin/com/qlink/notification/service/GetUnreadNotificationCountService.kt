package com.qlink.notification.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.dto.GetUnreadNotificationCountResponse
import com.qlink.notification.repository.NotificationRepository
import com.qlink.user.repository.UserRepository

class GetUnreadNotificationCountService(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getUnreadCount(loginId: Long): GetUnreadNotificationCountResponse =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)

            GetUnreadNotificationCountResponse(
                unreadCount = notificationRepository.countUnread(loginId),
            )
        }
}
