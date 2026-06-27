package com.qlink.auth.route

import com.qlink.auth.dto.AuthTokenResponse
import com.qlink.auth.dto.ConnectAuthProviderRequest
import com.qlink.auth.dto.ConnectAuthProviderResponse
import com.qlink.auth.dto.NativeRefreshTokenRequest
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.dto.SignOutRequest
import com.qlink.common.docs.authErrorResponse
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
                    examples(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, ErrorCode.AUTH_PROVIDER_TOKEN_INVALID)
                }
            }
        }
    }

internal fun connectAuthProviderDocs(): RouteConfig.() -> Unit =
    {
        summary = "소셜 인증 정보 연동 API"
        request { body<ConnectAuthProviderRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "인증 제공자 연동 성공"
                body<ApiResponse<ConnectAuthProviderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "지원하지 않는 인증 제공자"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "이미 연동된 인증 제공자"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_PROVIDER_ALREADY_CONNECTED)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "외부 인증 제공자 요청 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED, ErrorCode.AUTH_PROVIDER_TOKEN_INVALID)
                }
            }
        }
    }

internal fun webRefreshAuthTokenDocs(): RouteConfig.() -> Unit =
    {
        summary = "웹 토큰 갱신 API"
        response {
            code(HttpStatusCode.Created) {
                description = "토큰 갱신 성공"
                body<ApiResponse<AuthTokenResponse>>()
            }
            code(HttpStatusCode.Unauthorized) {
                description = "refresh token 인증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.AUTH_REFRESH_TOKEN_MISSING,
                        ErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED,
                    )
                }
            }
            code(HttpStatusCode.Forbidden) {
                description = "CSRF token 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_CSRF_TOKEN_INVALID)
                }
            }
        }
    }

internal fun signOutDocs(): RouteConfig.() -> Unit =
    {
        summary = "로그아웃 API"
        request { body<SignOutRequest>() }
        response {
            code(HttpStatusCode.OK) {
                description = "로그아웃 성공"
                body<ApiResponse<Unit>>()
            }
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.Forbidden) {
                description = "CSRF token 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AUTH_CSRF_TOKEN_INVALID)
                }
            }
        }
    }

internal fun nativeRefreshAuthTokenDocs(): RouteConfig.() -> Unit =
    {
        summary = "네이티브 토큰 갱신 API"
        request { body<NativeRefreshTokenRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "토큰 갱신 성공"
                body<ApiResponse<AuthTokenResponse>>()
            }
            code(HttpStatusCode.Unauthorized) {
                description = "refresh token 인증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.AUTH_REFRESH_TOKEN_MISSING,
                        ErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED,
                    )
                }
            }
        }
    }
