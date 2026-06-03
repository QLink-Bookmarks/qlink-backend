package com.qlink.ai.route

import com.qlink.ai.dto.AiProviderModelsResponse
import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.dto.PutAiUserProviderResponse
import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getAiProviderModelsDocs(): RouteConfig.() -> Unit =
    {
        summary = "AI Provider 설정 조회 API"
        response {
            code(HttpStatusCode.OK) {
                description = "AI Provider 설정 조회 성공"
                body<ApiResponse<List<AiProviderModelsResponse>>>()
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
        }
    }

internal fun putAiUserProviderDocs(): RouteConfig.() -> Unit =
    {
        summary = "AI API Key 등록 API"
        request { body<PutAiUserProviderRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "AI API Key 등록 성공"
                body<ApiResponse<PutAiUserProviderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "AI API Key 등록 요청 또는 API Key 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.AI_PROVIDER_NOT_SUPPORTED,
                        ErrorCode.AI_API_KEY_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "AI API Key 등록 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.USER_NOT_FOUND,
                        ErrorCode.AI_PROVIDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "AI 제공자 서비스 일시적 사용 불가"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.AI_VENDOR_TEMPORARY_UNAVAILABLE)
                }
            }
        }
    }
