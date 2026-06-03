package com.qlink.ai.service

import com.qlink.ai.client.AiApiKeyValidationException
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.dto.PutAiUserProviderResponse
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.crypto.ApiKeyCipher
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
    private val apiKeyCipher: ApiKeyCipher,
) {
    suspend fun putAiUserProvider(
        loginId: Long,
        request: PutAiUserProviderRequest,
    ): PutAiUserProviderResponse {
        val saved =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)
                val provider =
                    aiProviderRepository.findById(request.providerId)
                        ?: throw BusinessException(ErrorCode.AI_PROVIDER_NOT_FOUND)

                runCatching {
                    aiClientRouter.validateApiKey(
                        providerType = provider.type,
                        baseUrl = provider.baseUrl,
                        apiKey = request.apiKey,
                    )
                }.onFailure { throw it.toBusinessException() }

                val userProvider =
                    UserProvider(
                        userId = loginId,
                        providerId = provider.id!!,
                        apiKey = apiKeyCipher.encrypt(request.apiKey),
                    )

                userProviderRepository.save(userProvider)
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
