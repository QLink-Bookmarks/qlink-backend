package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

class AuthResourceClientRouter(
    clients: List<AuthResourceClient>,
) {
    private val clientsByProvider = clients.associateBy { it.providerType }

    suspend fun getResource(
        providerType: AuthProviderType,
        token: String,
    ): AuthResource {
        val client = clientsByProvider[providerType] ?: throw BusinessException(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED)

        return client.getResource(token)
    }
}
