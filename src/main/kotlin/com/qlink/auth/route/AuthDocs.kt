package com.qlink.auth.route

import com.qlink.auth.dto.AuthTokenResponse
import com.qlink.auth.dto.SignInRequest
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun signInDocs(): RouteConfig.() -> Unit =
    {
        summary = "인증 API"
        request { body<SignInRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "인증 성공"
                body<ApiResponse<AuthTokenResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "지원하지 않는 인증 제공자"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "외부 인증 제공자 요청 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)
                }
            }
        }
    }
