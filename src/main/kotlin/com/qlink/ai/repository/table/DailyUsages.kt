package com.qlink.ai.repository.table

import com.qlink.ai.domain.DailyUsage
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
    val userProviderId = reference("user_provider_id", UserProviders.id, onDelete = ReferenceOption.CASCADE)
    val modelId = reference("model_id", AvailableModels.id, onDelete = ReferenceOption.CASCADE)
    val usageDate = date("usage_date")
    val requests = integer("requests").default(0)
    val tokens = integer("tokens").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("daily_usages_model_id_idx", false, modelId)
        uniqueIndex("daily_usages_user_provider_model_date_unique", userProviderId, modelId, usageDate)
    }
}

fun ResultRow.toDailyUsageDomain(): DailyUsage =
    DailyUsage(
        id = this[DailyUsages.id],
        userProviderId = this[DailyUsages.userProviderId],
        modelId = this[DailyUsages.modelId],
        usageDate = this[DailyUsages.usageDate],
        requests = this[DailyUsages.requests],
        tokens = this[DailyUsages.tokens],
        createdAt = this[DailyUsages.createdAt].toKotlinInstant(),
        updatedAt = this[DailyUsages.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(dailyUsage: DailyUsage) {
    this[DailyUsages.userProviderId] = dailyUsage.userProviderId
    this[DailyUsages.modelId] = dailyUsage.modelId
    this[DailyUsages.usageDate] = dailyUsage.usageDate
    this[DailyUsages.requests] = dailyUsage.requests
    this[DailyUsages.tokens] = dailyUsage.tokens
}

fun UpdateBuilder<*>.refreshDailyUsageUpdatedAt() {
    this[DailyUsages.updatedAt] = Clock.System.now().toJavaInstant()
}
