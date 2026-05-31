package com.qlink.ai.client

data class AiClientConfig(
    val provider: AiProvider,
    val apiKey: String?,
    val model: String,
    val baseUrl: String,
)

data class AiClientRouterConfig(
    val defaultProvider: AiProvider,
)
