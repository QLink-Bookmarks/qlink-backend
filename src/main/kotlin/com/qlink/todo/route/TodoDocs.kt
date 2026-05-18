package com.qlink.todo.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.dto.CreateTodoResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun createTodoDocs(): RouteConfig.() -> Unit =
    {
        summary = "할 일 생성 API"
        request { body<CreateTodoRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "할 일 생성 성공"
                body<ApiResponse<CreateTodoResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "할 일 생성 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_TITLE_BLANK,
                        ErrorCode.TODO_TITLE_OVER_MAX,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "할 일 생성 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "할 일 생성에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_OWNER_NOT_FOUND,
                        ErrorCode.TODO_LINK_NOT_FOUND,
                    )
                }
            }
        }
    }
