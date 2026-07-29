package com.qlink.ai.service

import com.qlink.ai.dto.AiProviderResponse
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.common.transaction.TransactionRunner

class GetAiProvidersService(
    private val tx: TransactionRunner,
    private val aiProviderRepository: AiProviderRepository,
) {
    suspend fun getAiProviders(): List<AiProviderResponse> =
        tx.readOnly {
            aiProviderRepository
                .findAll()
                .map {
                    AiProviderResponse(
                        id = it.id!!,
                        type = it.type,
                    )
                }
        }
}
