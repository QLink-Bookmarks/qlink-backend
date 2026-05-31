@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.service

import kotlinx.serialization.Serializable

@Serializable
data class AiSummaryResponse(
    val linkId: Long,
    val status: AiSummaryStatus,
)

@Serializable
enum class AiSummaryStatus {
    QUEUED,
}
