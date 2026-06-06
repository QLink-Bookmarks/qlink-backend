@file:Suppress("ktlint:standard:filename")

package com.qlink.device.dto

import kotlinx.serialization.Serializable

@Serializable
data class PutDeviceResponse(
    val id: Long,
)
