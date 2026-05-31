package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType

data class AiClientConfig(
    val provider: AiProviderType,
    val apiKey: String?,
    val model: String,
    val baseUrl: String,
)

data class AiClientRouterConfig(
    val defaultProvider: AiProviderType,
)
