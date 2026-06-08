@file:Suppress("ktlint:standard:filename")

package com.qlink.support

import com.qlink.auth.client.AuthResource
import com.qlink.auth.client.AuthResourceClient
import com.qlink.auth.domain.AuthProviderType

class FakeAuthResourceClient : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.KAKAO
    var providerId: String = "kakao-user"
    var failure: Throwable? = null
    val requestedTokens: MutableList<String> = mutableListOf()

    override suspend fun getResource(token: String): AuthResource {
        requestedTokens.add(token)
        failure?.let { throw it }

        return AuthResource(
            providerType = providerType,
            providerId = providerId,
        )
    }

    fun reset() {
        providerId = "kakao-user"
        failure = null
        requestedTokens.clear()
    }
}
