package com.qlink.device.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.device.dto.PutDeviceRequest
import com.qlink.device.dto.PutDeviceResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun putDeviceDocs(): RouteConfig.() -> Unit =
    {
        summary = "디바이스 토큰 등록 API"
        request { body<PutDeviceRequest>() }
        response {
            code(HttpStatusCode.OK) {
                description = "디바이스 토큰 등록 성공"
                body<ApiResponse<PutDeviceResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "디바이스 토큰 등록 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.DEVICE_PLATFORM_NOT_SUPPORTED,
                        ErrorCode.DEVICE_TOKEN_BLANK,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "디바이스 토큰 등록에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
        }
    }

internal fun deleteDeviceDocs(): RouteConfig.() -> Unit =
    {
        summary = "디바이스 토큰 삭제 API"
        request {
            pathParameter<String>("token") { description = "삭제할 디바이스 토큰" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "디바이스 토큰 삭제 성공 (대상이 없어도 성공)"
                body<ApiResponse<EmptySuccessResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "다른 사용자의 디바이스 토큰 삭제 시도"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.DEVICE_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
        }
    }
