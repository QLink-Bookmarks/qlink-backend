package com.qlink.auth.service

import com.qlink.auth.client.AuthResourceClientRouter
import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.ConnectAuthProviderRequest
import com.qlink.auth.dto.ConnectAuthProviderResponse
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.repository.UserRepository

class ConnectAuthProviderService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val authProviderRepository: AuthProviderRepository,
    private val authResourceClientRouter: AuthResourceClientRouter,
) {
    suspend fun connect(
        loginId: Long,
        request: ConnectAuthProviderRequest,
    ): ConnectAuthProviderResponse {
        val providerType = AuthProviderType.fromRequestName(request.provider)

        tx.readOnly {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            val alreadyConnected =
                authProviderRepository
                    .findAllByUserId(loginId)
                    .any { it.providerType == providerType }
            if (alreadyConnected) {
                throw BusinessException(ErrorCode.AUTH_PROVIDER_ALREADY_CONNECTED)
            }
        }

        val resource =
            authResourceClientRouter.getResource(
                providerType = providerType,
                token = request.token,
                platform = request.platform,
            )

        return tx.required {
            val authProvider =
                authProviderRepository.insert(
                    AuthProvider(
                        userId = loginId,
                        providerType = resource.providerType,
                        providerId = resource.providerId,
                    ),
                )

            ConnectAuthProviderResponse(id = requireNotNull(authProvider.id))
        }
    }
}
