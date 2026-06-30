package com.qlink.device.repository

import com.qlink.device.domain.DeviceToken

interface DeviceTokenRepository {
    suspend fun insert(deviceToken: DeviceToken): DeviceToken

    suspend fun update(deviceToken: DeviceToken): DeviceToken

    suspend fun findByToken(token: String): DeviceToken?

    suspend fun findAllByUserId(userId: Long): List<DeviceToken>

    suspend fun deleteByToken(token: String)
}
