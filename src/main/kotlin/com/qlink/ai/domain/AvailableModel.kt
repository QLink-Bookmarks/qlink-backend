package com.qlink.ai.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import kotlin.time.Instant

class AvailableModel(
    val id: Long? = null,
    val providerId: Long,
    val model: String,
    val priority: Int,
    val rpdLimit: Int?,
    val tpdLimit: Int?,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun validateProvider(providerId: Long) {
        (this.providerId == providerId).requireTrue(ErrorCode.AI_MODEL_DIFFERENT_PROVIDER)
    }
}
