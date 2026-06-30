package com.qlink.device.route

import io.ktor.resources.Resource

@Resource("/devices")
class DeviceResources {
    @Resource("{token}")
    class ByToken(
        val parent: DeviceResources = DeviceResources(),
        val token: String,
    )
}
