package com.qlink.ai.client

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

class AiClientRouter(
    private val config: AiClientRouterConfig,
    clients: List<AiClient>,
) {
    private val clientsByProvider = clients.associateBy { it.provider }

    suspend fun summarize(
        provider: AiProvider?,
        prompt: AiSummaryPrompt,
    ): String {
        val selectedProvider = provider ?: config.defaultProvider
        val client = clientsByProvider[selectedProvider] ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED)

        return client.summarize(prompt)
    }
}
