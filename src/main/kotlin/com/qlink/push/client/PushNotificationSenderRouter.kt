package com.qlink.push.client

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.device.domain.DevicePlatform

class PushNotificationSenderRouter(
    senders: List<PushNotificationSender>,
) {
    private val sendersByPlatform: Map<DevicePlatform, PushNotificationSender> = senders.associateBy { it.platform }

    fun findByPlatform(platform: DevicePlatform): PushNotificationSender =
        sendersByPlatform[platform] ?: throw BusinessException(ErrorCode.PUSH_PLATFORM_NOT_SUPPORTED)
}
