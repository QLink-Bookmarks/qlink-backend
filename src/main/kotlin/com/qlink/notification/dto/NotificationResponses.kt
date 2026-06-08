@file:Suppress("ktlint:standard:filename")

package com.qlink.notification.dto

import com.qlink.notification.domain.NotificationContext
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class GetNotificationsContentResponse(
    val id: Long,
    val title: String,
    val message: String,
    val firedAt: Instant,
    val readAt: Instant?,
    val context: NotificationContext,
    val contextId: Long,
)

@Serializable
data class GetUnreadNotificationCountResponse(
    val unreadCount: Int,
)
