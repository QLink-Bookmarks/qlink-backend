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

class NaverAuthResourceClient(
    private val httpClient: HttpClient,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.NAVER

    override suspend fun getResource(
        token: String,
        platform: AuthPlatform,
    ): AuthResource {
        val response =
            runCatching {
                httpClient.get(NAVER_USER_INFO_URL) {
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
                response.body<NaverUserResponse>()
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it)
            }

        return AuthResource(
            providerType = providerType,
            providerId = body.response.id,
        )
    }

    private companion object {
        const val NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me"
    }
}

@Serializable
private data class NaverUserResponse(
    val response: NaverUserDetail,
)

@Serializable
private data class NaverUserDetail(
    val id: String,
)
