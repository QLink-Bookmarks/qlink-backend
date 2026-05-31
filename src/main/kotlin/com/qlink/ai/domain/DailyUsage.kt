package com.qlink.ai.domain

import java.time.LocalDate
import kotlin.time.Instant

class DailyUsage(
    val id: Long? = null,
    val userId: Long,
    val providerId: Long,
    val usageDate: LocalDate,
    val requestCount: Int = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
