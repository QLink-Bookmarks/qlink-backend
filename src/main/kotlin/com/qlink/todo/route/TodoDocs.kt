package com.qlink.todo.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.todo.dto.CompleteTodoRequest
import com.qlink.todo.dto.CompleteTodoResponse
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.dto.CreateTodoResponse
import com.qlink.todo.dto.UpdateTodoRequest
import com.qlink.todo.dto.UpdateTodoResponse
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

internal fun completeTodoDocs(): RouteConfig.() -> Unit =
    {
        summary = "할 일 완료 상태 변경 API"
        request { body<CompleteTodoRequest>() }
        response {
            code(HttpStatusCode.OK) {
                description = "할 일 완료 상태 변경 성공"
                body<ApiResponse<CompleteTodoResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "할 일 완료 상태 변경 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.TODO_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "할 일 완료 상태 변경에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_OWNER_NOT_FOUND,
                        ErrorCode.TODO_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun updateTodoDocs(): RouteConfig.() -> Unit =
    {
        summary = "할 일 수정 API"
        request { body<UpdateTodoRequest>() }
        response {
            code(HttpStatusCode.OK) {
                description = "할 일 수정 성공"
                body<ApiResponse<UpdateTodoResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "할 일 수정 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_TITLE_BLANK,
                        ErrorCode.TODO_TITLE_OVER_MAX,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "할 일 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_DIFFERENT_OWNER,
                        ErrorCode.LINK_DIFFERENT_OWNER,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "할 일 수정에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.TODO_OWNER_NOT_FOUND,
                        ErrorCode.TODO_NOT_FOUND,
                        ErrorCode.TODO_LINK_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun deleteTodoDocs(): RouteConfig.() -> Unit =
    {
        summary = "할 일 삭제 API"
        response {
            code(HttpStatusCode.OK) {
                description = "할 일 삭제 성공"
                body<EmptySuccessResponse>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "할 일 삭제 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.TODO_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "할 일 삭제 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.TODO_OWNER_NOT_FOUND)
                }
            }
        }
    }
