package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class KakaoAuthResourceClient(
    private val httpClient: HttpClient,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.KAKAO

    override suspend fun getResource(
        token: String,
        platform: AuthPlatform,
    ): AuthResource {
        val response =
            runCatching {
                httpClient.get(KAKAO_USER_ME_URL) {
                    bearerAuth(token)
                }
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, it)
            }

        if (!response.status.isSuccess()) {
            throw BusinessException(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID)
        }

        val body =
            runCatching {
                response.body<KakaoUserResponse>()
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, it)
            }

        return AuthResource(
            providerType = providerType,
            providerId = body.id.toString(),
        )
    }

    private companion object {
        const val KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me"
    }
}

@Serializable
private data class KakaoUserResponse(
    val id: Long,
)
