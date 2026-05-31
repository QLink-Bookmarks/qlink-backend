package com.qlink.ai.domain

import java.time.LocalDate
import kotlin.time.Instant

class DailyUsage(
    val id: Long? = null,
    val userProviderId: Long,
    val modelId: Long,
    val usageDate: LocalDate,
    val requests: Int = 0,
    val tokens: Int = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun addUsage(tokens: Int): DailyUsage =
        DailyUsage(
            id = id,
            userProviderId = userProviderId,
            modelId = modelId,
            usageDate = usageDate,
            requests = requests + 1,
            tokens = this.tokens + tokens,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun isOverLimit(model: AvailableModel): Boolean =
        (model.rpdLimit != null && requests >= model.rpdLimit) ||
            (model.tpdLimit != null && tokens >= model.tpdLimit)
}
