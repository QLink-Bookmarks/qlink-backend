package com.qlink.ai.service

import com.qlink.ai.client.AiApiKeyValidationException
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.crypto.AiApiKeyCipher
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.dto.PutAiUserProviderResponse
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.repository.UserRepository

class PutAiUserProviderService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val userProviderRepository: UserProviderRepository,
    private val aiClientRouter: AiClientRouter,
    private val apiKeyCipher: AiApiKeyCipher,
) {
    suspend fun putAiUserProvider(
        loginId: Long,
        request: PutAiUserProviderRequest,
    ): PutAiUserProviderResponse {
        val provider =
            tx.readOnly {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)
                aiProviderRepository.findById(request.providerId)
                    ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_FOUND)
            }

        runCatching {
            aiClientRouter.validateApiKey(
                providerType = provider.type,
                baseUrl = provider.baseUrl,
                apiKey = request.apiKey,
            )
        }.onFailure { throw it.toBusinessException() }

        val encryptedApiKey = apiKeyCipher.encrypt(request.apiKey)
        val saved =
            tx.required {
                val existing =
                    userProviderRepository.findByUserIdAndProviderId(
                        userId = loginId,
                        providerId = provider.id!!,
                    )
                val userProvider =
                    UserProvider(
                        id = existing?.id,
                        userId = loginId,
                        providerId = provider.id,
                        userRole = existing?.userRole ?: Role.NORMAL,
                        apiKey = encryptedApiKey,
                    )

                if (existing == null) {
                    userProviderRepository.insert(userProvider)
                } else {
                    userProviderRepository.update(userProvider)
                }
            }

        return PutAiUserProviderResponse(id = saved.id!!)
    }

    private fun Throwable.toBusinessException(): BusinessException =
        when (this) {
            is BusinessException -> {
                this
            }

            is AiApiKeyValidationException -> {
                when (statusCode) {
                    401, 403 -> BusinessException(ErrorCode.AI_API_KEY_INVALID, this)
                    in 500..599 -> BusinessException(ErrorCode.AI_VENDOR_TEMPORARY_UNAVAILABLE, this)
                    else -> BusinessException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR, this)
                }
            }

            else -> {
                BusinessException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR, this)
            }
        }
}
