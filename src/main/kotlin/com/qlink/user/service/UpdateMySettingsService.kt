package com.qlink.user.service

import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.domain.UserAccent
import com.qlink.user.domain.UserTheme
import com.qlink.user.dto.UpdateMySettingsRequest
import com.qlink.user.repository.UserRepository

class UpdateMySettingsService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
) {
    suspend fun updateMySettings(
        loginId: Long,
        request: UpdateMySettingsRequest,
    ) {
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val theme = request.theme?.let { UserTheme.fromRequestName(it) }
            val accent = request.accent?.let { UserAccent.fromRequestName(it) }

            val targetProviderId = request.defaultProviderId ?: user.defaultAiProviderId
            val targetModelId = request.defaultModelId ?: user.defaultModelId
            val targetModel =
                targetModelId?.let { modelId ->
                    availableModelRepository.findById(modelId) ?: throw BusinessException(ErrorCode.AI_MODEL_NOT_FOUND)
                }

            request.defaultProviderId?.let {
                aiProviderRepository.findById(it) ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_FOUND)
            }
            targetProviderId?.let { providerId -> targetModel?.validateProvider(providerId) }

            val changed =
                user.changeSettings(
                    theme = theme,
                    accent = accent,
                    allowsReminder = request.allowsReminder,
                    defaultAiProviderId = request.defaultProviderId,
                    defaultModelId = request.defaultModelId,
                )

            if (!user.hasSameSettings(changed)) {
                userRepository.update(changed)
            }
        }
    }
}
