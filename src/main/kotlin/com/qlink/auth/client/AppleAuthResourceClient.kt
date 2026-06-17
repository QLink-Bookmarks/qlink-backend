package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.config.AppleConfig
import io.ktor.client.HttpClient

/**
 * 애플은 웹/네이티브 모두 클라이언트가 넘긴 id_token(JWT)을 애플 공개키(JWKS)로 검증한 뒤
 * `sub` 클레임을 providerId로 추출한다.
 */
class AppleAuthResourceClient(
    httpClient: HttpClient,
    private val appleConfig: AppleConfig,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.APPLE

    private val idTokenVerifier =
        OidcIdTokenVerifier(
            httpClient = httpClient,
            jwksUrl = APPLE_PUBLIC_KEYS_URL,
            issuers = listOf(APPLE_ISSUER),
        )

    override suspend fun getResource(
        token: String,
        platform: AuthPlatform,
    ): AuthResource {
        val subject = idTokenVerifier.verifyAndGetSubject(token, appleConfig.clientIds)
        return AuthResource(
            providerType = providerType,
            providerId = subject,
        )
    }

    private companion object {
        const val APPLE_ISSUER = "https://appleid.apple.com"
        const val APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys"
    }
}
