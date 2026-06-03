package com.qlink.support.fixture

import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken

object DeviceTokenFixture {
    fun createRandomValidDeviceToken(
        userId: Long = RandomFixture.randomId(),
        platform: DevicePlatform = DevicePlatform.ANDROID,
        token: String = randomDeviceToken(),
    ): DeviceToken =
        DeviceToken(
            userId = userId,
            platform = platform,
            token = token,
        )

    fun randomDeviceToken(): String = "device-token-${RandomFixture.randomId()}"
}
