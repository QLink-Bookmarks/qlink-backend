package com.qlink.notification.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.domain.DeviceToken
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.push.client.PushNotificationSendRequest
import com.qlink.push.client.PushNotificationSenderRouter
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class SendNotificationService(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val senderRouter: PushNotificationSenderRouter,
    private val todoRepository: TodoRepository,
    private val linkRepository: LinkRepository,
) {
    suspend fun send(notificationId: Long): SendNotificationResult {
        val target = findSendTarget(notificationId)
        val sentResults = sendOutsideTransaction(target)

        val successCount = sentResults.count { it.success }
        val failureCount = sentResults.count { !it.success }

        val updatedNotification =
            tx.required {
                val savedNotification =
                    notificationRepository.update(
                        target.notification.recordSendResult(
                            handledAt = Clock.System.now(),
                            successCount = successCount,
                            failureCount = failureCount,
                        ),
                    )

                if (savedNotification.firedAt != null) {
                    createNextTodoNotificationIfNeeded(savedNotification)
                }

                savedNotification
            }

        return SendNotificationResult(
            notificationId = updatedNotification.id!!,
            successCount = updatedNotification.successCount,
            failureCount = updatedNotification.failureCount,
        )
    }

    private suspend fun findSendTarget(notificationId: Long): SendNotificationTarget =
        tx.readOnly {
            val notification =
                notificationRepository.findPendingById(notificationId)
                    ?: throw BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)
            val user = userRepository.findById(notification.userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val deviceTokens =
                if (user.allowsReminder) {
                    deviceTokenRepository.findAllByUserId(user.id!!)
                } else {
                    emptyList()
                }

            SendNotificationTarget(
                notification = notification,
                allowsReminder = user.allowsReminder,
                deviceTokens = deviceTokens,
            )
        }

    private suspend fun sendOutsideTransaction(target: SendNotificationTarget) =
        if (target.allowsReminder) {
            target.deviceTokens.map { deviceToken ->
                senderRouter
                    .findByPlatform(deviceToken.platform)
                    .send(target.notification.toSendRequest(deviceToken))
            }
        } else {
            emptyList()
        }

    private fun Notification.toSendRequest(deviceToken: DeviceToken): PushNotificationSendRequest =
        PushNotificationSendRequest(
            token = deviceToken.token,
            title = title,
            message = message,
            data =
                mapOf(
                    "notificationId" to id.toString(),
                    "context" to context.name,
                    "contextId" to contextId.toString(),
                ),
        )

    private suspend fun createNextTodoNotificationIfNeeded(notification: Notification) {
        if (!notification.isTodo) {
            return
        }

        val todo =
            todoRepository.findById(notification.contextId)
                ?: throw BusinessException(ErrorCode.TODO_NOT_FOUND)
        if (!todo.hasRepeat) {
            return
        }

        val savedTodo = todoRepository.update(todo.setNextReminder(Clock.System.now()))
        val link = linkRepository.findById(savedTodo.linkId) ?: return
        Notification
            .todo(todo = savedTodo, linkTitle = link.title, linkUrl = link.url)
            ?.takeUnless { nextNotification ->
                notificationRepository
                    .findPendingByContext(
                        context = NotificationContext.TODO,
                        contextId = savedTodo.id!!,
                    ).any { it.willFireAt == nextNotification.willFireAt }
            }?.let { notificationRepository.insert(it) }
    }
}

data class SendNotificationResult(
    val notificationId: Long,
    val successCount: Int,
    val failureCount: Int,
)

private data class SendNotificationTarget(
    val notification: Notification,
    val allowsReminder: Boolean,
    val deviceTokens: List<DeviceToken>,
)
