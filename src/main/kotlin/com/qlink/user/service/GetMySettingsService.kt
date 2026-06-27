package com.qlink.user.service

import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.dto.GetMySettingsResponse
import com.qlink.user.dto.UserAiSettingsResponse
import com.qlink.user.dto.UserAuthProviderResponse
import com.qlink.user.dto.UserBehaviorSettingsResponse
import com.qlink.user.dto.UserDefaultModelResponse
import com.qlink.user.dto.UserDefaultProviderResponse
import com.qlink.user.dto.UserDisplaySettingsResponse
import com.qlink.user.repository.UserRepository

class GetMySettingsService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
    private val authProviderRepository: AuthProviderRepository,
) {
    suspend fun getMySettings(loginId: Long): GetMySettingsResponse =
        tx.readOnly {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val defaultProvider = user.defaultAiProviderId?.let { aiProviderRepository.findById(it) }
            val defaultModel = user.defaultModelId?.let { availableModelRepository.findById(it) }
            val authProviders = authProviderRepository.findAllByUserId(loginId)

            GetMySettingsResponse(
                display =
                    UserDisplaySettingsResponse(
                        theme = user.theme.responseName,
                        accent = user.accent.responseName,
                    ),
                behavior =
                    UserBehaviorSettingsResponse(
                        allowsReminderNotification = user.allowsReminder,
                    ),
                ai =
                    UserAiSettingsResponse(
                        defaultProvider =
                            UserDefaultProviderResponse(
                                id = defaultProvider?.id,
                                type = defaultProvider?.type?.name,
                            ),
                        defaultModel =
                            UserDefaultModelResponse(
                                id = defaultModel?.id,
                                model = defaultModel?.model,
                            ),
                    ),
                providers =
                    authProviders.map {
                        UserAuthProviderResponse(
                            id = requireNotNull(it.id),
                            type = it.providerType.name,
                        )
                    },
            )
        }
}
