package com.qlink.ai.service

import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
import com.qlink.ai.dto.AiProviderModelResponse
import com.qlink.ai.dto.AiProviderModelsResponse
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.repository.UserRepository

private const val DEFAULT_MODEL_NAME = "DEFAULT"

class GetAiProviderModelsService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val userProviderRepository: UserProviderRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
) {
    suspend fun getAiProviderModels(loginId: Long?): List<AiProviderModelsResponse> =
        tx.readOnly {
            loginId?.let { userRepository.emptyById(it).requireFalse(ErrorCode.USER_NOT_FOUND) }

            val userProviders = loginId?.let { userProviderRepository.findAllByUserId(it) }.orEmpty()
            val superAdminProviders = userProviderRepository.findAllByRole(UserProviderRole.SUPER_ADMIN)
            val selectedUserProviders =
                userProviders.associateBy { it.providerId } +
                    superAdminProviders
                        .filterNot { superAdminProvider -> userProviders.any { it.providerId == superAdminProvider.providerId } }
                        .associateBy { it.providerId }

            selectedUserProviders.values
                .map { userProvider ->
                    userProvider.toResponse(isDefaultProvider = userProvider.userRole == UserProviderRole.SUPER_ADMIN)
                }.sortedWith(compareBy({ it.providerType.name }, { it.providerId }))
        }

    private suspend fun UserProvider.toResponse(isDefaultProvider: Boolean): AiProviderModelsResponse {
        val provider = aiProviderRepository.findById(providerId) ?: throw BusinessException(ErrorCode.AI_USER_PROVIDER_NOT_FOUND)
        val models = availableModelRepository.findAllByProviderId(providerId)

        return AiProviderModelsResponse(
            providerId = provider.id!!,
            providerType = provider.type,
            models =
                if (isDefaultProvider) {
                    models.take(1).map {
                        AiProviderModelResponse(
                            id = it.id!!,
                            model = DEFAULT_MODEL_NAME,
                            priority = it.priority,
                        )
                    }
                } else {
                    models.map {
                        AiProviderModelResponse(
                            id = it.id!!,
                            model = it.model,
                            priority = it.priority,
                        )
                    }
                },
        )
    }
}
