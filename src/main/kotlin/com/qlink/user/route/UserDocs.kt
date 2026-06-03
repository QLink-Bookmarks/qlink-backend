package com.qlink.user.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.user.dto.GetMyProfileResponse
import com.qlink.user.dto.GetMySettingsResponse
import com.qlink.user.dto.UpdateMySettingsRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getMyProfileDocs(): RouteConfig.() -> Unit =
    {
        summary = "내 정보 조회 API"
        response {
            code(HttpStatusCode.OK) {
                description = "내 정보 조회 성공"
                body<ApiResponse<GetMyProfileResponse>>()
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

internal fun getMySettingsDocs(): RouteConfig.() -> Unit =
    {
        summary = "사용자 설정 조회 API"
        response {
            code(HttpStatusCode.OK) {
                description = "사용자 설정 조회 성공"
                body<ApiResponse<GetMySettingsResponse>>()
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

internal fun updateMySettingsDocs(): RouteConfig.() -> Unit =
    {
        summary = "사용자 설정 변경 API"
        request { body<UpdateMySettingsRequest>() }
        response {
            code(HttpStatusCode.OK) {
                description = "사용자 설정 변경 성공"
                body<ApiResponse<EmptySuccessResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "사용자 설정 변경 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.USER_THEME_NOT_SUPPORTED,
                        ErrorCode.USER_ACCENT_NOT_SUPPORTED,
                        ErrorCode.AI_MODEL_DIFFERENT_PROVIDER,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "사용자 설정 변경 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.USER_NOT_FOUND,
                        ErrorCode.AI_PROVIDER_NOT_FOUND,
                        ErrorCode.AI_MODEL_NOT_FOUND,
                    )
                }
            }
        }
    }
