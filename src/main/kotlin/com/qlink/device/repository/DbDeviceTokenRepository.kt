package com.qlink.device.repository

import com.qlink.device.domain.DeviceToken
import com.qlink.device.repository.table.DeviceTokens
import com.qlink.device.repository.table.fromDomain
import com.qlink.device.repository.table.refreshDeviceTokenUpdatedAt
import com.qlink.device.repository.table.toDeviceTokenDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsertReturning

class DbDeviceTokenRepository : DeviceTokenRepository {
    override suspend fun save(deviceToken: DeviceToken): DeviceToken =
        DeviceTokens
            .upsertReturning(
                DeviceTokens.token,
                onUpdate = {
                    it[DeviceTokens.userId] = deviceToken.userId
                    it[DeviceTokens.platform] = deviceToken.platform
                    it.refreshDeviceTokenUpdatedAt()
                },
            ) {
                it.fromDomain(deviceToken)
            }.single()
            .toDeviceTokenDomain()

    override suspend fun findByToken(token: String): DeviceToken? =
        DeviceTokens
            .selectAll()
            .where { DeviceTokens.token eq token }
            .singleOrNull()
            ?.toDeviceTokenDomain()

    override suspend fun findAllByUserId(userId: Long): List<DeviceToken> =
        DeviceTokens
            .selectAll()
            .where { DeviceTokens.userId eq userId }
            .orderBy(DeviceTokens.id to SortOrder.ASC)
            .map { it.toDeviceTokenDomain() }
}
