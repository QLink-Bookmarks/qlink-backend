package com.qlink.notification.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import com.qlink.notification.dto.GetNotificationsContentResponse
import com.qlink.notification.dto.GetUnreadNotificationCountResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getNotificationsDocs(): RouteConfig.() -> Unit =
    {
        summary = "알림 목록 조회 API"
        request {
            queryParameter<String?>("query") { description = "제목/메시지 검색어" }
            queryParameter<String>("order") { description = "정렬 기준, 기본값: latest" }
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 30" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "알림 목록 조회 성공"
                body<ApiResponse<ScrollResponse<GetNotificationsContentResponse>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "알림 목록 조회 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_INVALID_SORT_ORDER,
                        ErrorCode.COMMON_CURSOR_MALFORMED,
                        ErrorCode.COMMON_CURSOR_ORDER_MISMATCH,
                        ErrorCode.COMMON_CURSOR_FIELD_MISSING,
                    )
                }
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

internal fun getUnreadNotificationCountDocs(): RouteConfig.() -> Unit =
    {
        summary = "안 읽은 알림 집계 API"
        response {
            code(HttpStatusCode.OK) {
                description = "안 읽은 알림 집계 성공"
                body<ApiResponse<GetUnreadNotificationCountResponse>>()
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

internal fun readNotificationDocs(): RouteConfig.() -> Unit =
    {
        summary = "알림 읽음처리 API"
        request {
            pathParameter<Long>("id") { description = "알림 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "알림 읽음처리 성공"
                body<EmptySuccessResponse>()
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.USER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "알림 읽음처리 대상 상태가 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.NOTIFICATION_NOT_FIRED)
                }
            }
        }
    }
