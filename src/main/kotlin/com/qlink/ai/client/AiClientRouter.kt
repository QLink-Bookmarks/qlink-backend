package com.qlink.ai.client

import com.qlink.ai.domain.AiProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

class AiClientRouter(
    private val config: AiClientRouterConfig,
    clients: List<AiClient>,
) {
    private val clientsByProvider = clients.associateBy { it.providerType }

    suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse {
        val client = clientsByProvider[request.providerType] ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED)

        return client.summarize(request)
    }

    fun requireSupported(providerType: AiProviderType): AiClient =
        clientsByProvider[providerType] ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED)
}
