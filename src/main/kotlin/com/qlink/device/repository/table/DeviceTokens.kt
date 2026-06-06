package com.qlink.device.repository.table

import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toKotlinInstant

object DeviceTokens : Table("device_tokens") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName<DevicePlatform>("platform", 20)
    val token = text("token")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("device_tokens_token_unique", token)
        index("device_tokens_user_id_idx", false, userId)
    }
}

fun ResultRow.toDeviceTokenDomain(): DeviceToken =
    DeviceToken(
        id = this[DeviceTokens.id],
        userId = this[DeviceTokens.userId],
        platform = this[DeviceTokens.platform],
        token = this[DeviceTokens.token],
        createdAt = this[DeviceTokens.createdAt].toKotlinInstant(),
        updatedAt = this[DeviceTokens.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(deviceToken: DeviceToken) {
    this[DeviceTokens.userId] = deviceToken.userId
    this[DeviceTokens.platform] = deviceToken.platform
    this[DeviceTokens.token] = deviceToken.token
}
