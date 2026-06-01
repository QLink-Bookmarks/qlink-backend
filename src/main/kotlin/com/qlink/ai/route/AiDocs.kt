package com.qlink.ai.route

import com.qlink.ai.dto.AiProviderModelsResponse
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
