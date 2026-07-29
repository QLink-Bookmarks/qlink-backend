@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.dto

import com.qlink.ai.domain.AiProviderType
import kotlinx.serialization.Serializable

@Serializable
data class AiProviderResponse(
    val id: Long,
    val type: AiProviderType,
)

@Serializable
data class PutAiUserProviderResponse(
    val id: Long,
)
