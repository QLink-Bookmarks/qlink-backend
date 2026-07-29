package com.qlink.ai.route

import com.qlink.ai.dto.AiProviderModelsResponse
import com.qlink.ai.dto.AiProviderResponse
import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.dto.PutAiUserProviderResponse
import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getAiProvidersDocs(): RouteConfig.() -> Unit =
    {
        summary = "AI Provider 목록 조회 API"
        description = "지원하는 AI Provider 목록을 조회합니다. 인증이 필요하지 않습니다."
        response {
            code(HttpStatusCode.OK) {
                description = "AI Provider 목록 조회 성공"
                body<ApiResponse<List<AiProviderResponse>>>()
            }
        }
    }

internal fun getAiProviderModelsDocs(): RouteConfig.() -> Unit =
    {
        summary = "AI Provider 설정 조회 API"
        description =
            "isMine=true 면 로그인 사용자의 AI Provider 설정을 조회하고, " +
            "isMine=false 면 인증 없이 AI Provider별 사용 가능 모델을 조회합니다."
        request {
            queryParameter<Boolean>("isMine") { description = "로그인 사용자 설정 조회 여부 (기본값 false)" }
        }
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
