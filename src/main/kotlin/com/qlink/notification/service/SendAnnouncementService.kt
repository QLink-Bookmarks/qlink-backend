package com.qlink.notification.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.repository.NotificationRepository
import com.qlink.push.client.PushNotificationSendRequest
import com.qlink.push.client.PushNotificationSenderRouter
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

private const val ANNOUNCE_TITLE = "에이링크 공지사항"
private const val DEFAULT_MULTICAST_CHUNK = 100

private val MULTICAST_CHUNK_SIZE =
    mapOf(
        DevicePlatform.WEB to 500,
        DevicePlatform.NATIVE to 100,
    )

/**
 * Online (immediate) announcement fan-out: records an in-app notification for every user and
 * multicasts a device push per platform. Not scheduled — one call sends the whole broadcast.
 */
class SendAnnouncementService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val senderRouter: PushNotificationSenderRouter,
) {
    suspend fun sendForPost(
        postId: Long,
        postTitle: String,
    ) {
        val at = Clock.System.now()

        val deviceTokens =
            tx.required {
                val userIds = userRepository.findAllIds()
                userIds.forEach { userId ->
                    notificationRepository.insert(
                        Notification
                            .announce(userId = userId, postId = postId, postTitle = postTitle, at = at)
                            .markFired(at),
                    )
                }
                deviceTokenRepository.findAllByUserIds(userIds)
            }

        pushMulticast(deviceTokens, postId, postTitle)
    }

    private suspend fun pushMulticast(
        deviceTokens: List<DeviceToken>,
        postId: Long,
        postTitle: String,
    ) {
        deviceTokens.groupBy { it.platform }.forEach { (platform, tokens) ->
            val sender = senderRouter.findByPlatform(platform)
            val chunkSize = MULTICAST_CHUNK_SIZE[platform] ?: DEFAULT_MULTICAST_CHUNK
            tokens.chunked(chunkSize).forEach { chunk ->
                sender.sendMulticast(chunk.map { announceRequest(it.token, postId, postTitle) })
            }
        }
    }

    private fun announceRequest(
        token: String,
        postId: Long,
        postTitle: String,
    ): PushNotificationSendRequest =
        PushNotificationSendRequest(
            token = token,
            title = ANNOUNCE_TITLE,
            message = "새로운 공지사항이 있어요. $postTitle",
            data =
                mapOf(
                    "context" to "ANNOUNCE",
                    "contextId" to postId.toString(),
                ),
        )
}
