package com.qlink.ai.repository.table

import com.qlink.ai.domain.DailyUsage
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object DailyUsages : Table("daily_usages") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val providerId = reference("provider_id", AiProviders.id, onDelete = ReferenceOption.CASCADE)
    val usageDate = date("usage_date")
    val requestCount = integer("request_count").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("daily_usages_provider_id_idx", false, providerId)
        uniqueIndex("daily_usages_user_provider_date_unique", userId, providerId, usageDate)
    }
}

fun ResultRow.toDailyUsageDomain(): DailyUsage =
    DailyUsage(
        id = this[DailyUsages.id],
        userId = this[DailyUsages.userId],
        providerId = this[DailyUsages.providerId],
        usageDate = this[DailyUsages.usageDate],
        requestCount = this[DailyUsages.requestCount],
        createdAt = this[DailyUsages.createdAt].toKotlinInstant(),
        updatedAt = this[DailyUsages.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(dailyUsage: DailyUsage) {
    this[DailyUsages.userId] = dailyUsage.userId
    this[DailyUsages.providerId] = dailyUsage.providerId
    this[DailyUsages.usageDate] = dailyUsage.usageDate
    this[DailyUsages.requestCount] = dailyUsage.requestCount
}

fun UpdateBuilder<*>.refreshDailyUsageUpdatedAt() {
    this[DailyUsages.updatedAt] = Clock.System.now().toJavaInstant()
}
