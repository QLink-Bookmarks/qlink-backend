package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class GoogleAuthResourceClient(
    private val httpClient: HttpClient,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.GOOGLE

    override suspend fun getResource(token: String): AuthResource {
        val response =
            runCatching {
                httpClient.get(GOOGLE_USER_INFO_URL) {
                    bearerAuth(token)
                }
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it)
            }

        if (!response.status.isSuccess()) {
            throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)
        }

        val body =
            runCatching {
                response.body<GoogleUserResponse>()
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it)
            }

        return AuthResource(
            providerType = providerType,
            providerId = body.sub,
        )
    }

    private companion object {
        const val GOOGLE_USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
    }
}

@Serializable
private data class GoogleUserResponse(
    val sub: String,
)
