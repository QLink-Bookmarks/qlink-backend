package com.qlink.user.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.user.dto.GetMyProfileResponse
import com.qlink.user.dto.GetMySettingsResponse
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
