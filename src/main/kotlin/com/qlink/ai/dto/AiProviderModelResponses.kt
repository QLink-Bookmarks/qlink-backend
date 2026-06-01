@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.dto

import com.qlink.ai.domain.AiProviderType
import kotlinx.serialization.Serializable

@Serializable
data class AiProviderModelsResponse(
    val providerId: Long,
    val providerType: AiProviderType,
    val models: List<AiProviderModelResponse>,
)

@Serializable
data class AiProviderModelResponse(
    val id: Long,
    val model: String,
    val priority: Int,
    val userProviderId: Long,
)
