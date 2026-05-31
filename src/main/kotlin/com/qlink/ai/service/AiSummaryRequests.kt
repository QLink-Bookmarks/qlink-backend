@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.service

import com.qlink.ai.client.AiProvider
import kotlinx.serialization.Serializable

@Serializable
data class AiSummaryRequest(
    val linkId: Long,
    val provider: AiProvider? = null,
)
