package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.GoogleConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * 구글은 플랫폼에 따라 클라이언트가 넘기는 토큰 종류가 다르다.
 * - WEB: access token → userinfo API로 사용자 정보를 조회해 `sub`를 추출
 * - NATIVE: id_token(JWT) → 구글 공개키(JWKS)로 검증한 뒤 `sub`를 추출
 */
class GoogleAuthResourceClient(
    private val httpClient: HttpClient,
    private val googleConfig: GoogleConfig,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.GOOGLE

    private val idTokenVerifier =
        OidcIdTokenVerifier(
            httpClient = httpClient,
            jwksUrl = GOOGLE_CERTS_URL,
            issuers = GOOGLE_ISSUERS,
        )

    override suspend fun getResource(
        token: String,
        platform: AuthPlatform,
    ): AuthResource =
        when (platform) {
            AuthPlatform.WEB -> fromAccessToken(token)
            AuthPlatform.NATIVE -> fromIdToken(token)
        }

    private suspend fun fromAccessToken(accessToken: String): AuthResource {
        val response =
            runCatching {
                httpClient.get(GOOGLE_USER_INFO_URL) {
                    bearerAuth(accessToken)
                }
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, it)
            }

        if (!response.status.isSuccess()) {
            throw BusinessException(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID)
        }

        val body =
            runCatching {
                response.body<GoogleUserResponse>()
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, it)
            }

        return AuthResource(
            providerType = providerType,
            providerId = body.sub,
        )
    }

    private suspend fun fromIdToken(idToken: String): AuthResource {
        val subject = idTokenVerifier.verifyAndGetSubject(idToken, googleConfig.clientIds)
        return AuthResource(
            providerType = providerType,
            providerId = subject,
        )
    }

    private companion object {
        const val GOOGLE_USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
        const val GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        val GOOGLE_ISSUERS = listOf("https://accounts.google.com", "accounts.google.com")
    }
}

@Serializable
private data class GoogleUserResponse(
    val sub: String,
)
