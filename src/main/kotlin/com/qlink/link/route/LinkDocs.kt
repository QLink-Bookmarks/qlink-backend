package com.qlink.link.route

import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.dto.AiSummaryResponse
import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.dto.GetLinkDetailResponse
import com.qlink.link.dto.GetLinksContentResponse
import com.qlink.link.dto.PatchLinkRequest
import com.qlink.link.dto.PatchLinkResponse
import com.qlink.link.dto.UpdateLinkRequest
import com.qlink.link.dto.UpdateLinkResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun createLinkDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 생성 API"
        request { body<CreateLinkRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "링크 생성 성공"
                body<ApiResponse<CreateLinkResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 생성 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_URL_BLANK,
                        ErrorCode.LINK_URL_WRONG_FORMAT,
                        ErrorCode.LINK_URL_NOT_HTTP,
                        ErrorCode.LINK_URL_WRONG_HOST,
                        ErrorCode.LINK_TITLE_BLANK,
                        ErrorCode.LINK_TITLE_OVER_MAX,
                        ErrorCode.TODO_TITLE_BLANK,
                        ErrorCode.TODO_TITLE_OVER_MAX,
                        ErrorCode.TODO_REPEAT_FIELDS_INCOMPLETE,
                        ErrorCode.TODO_REPEAT_DAYS_EMPTY,
                        ErrorCode.TODO_REPEAT_TIME_INVALID,
                        ErrorCode.TODO_REPEAT_TIMEZONE_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 생성 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 생성에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_FOLDER_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun updateLinkAiSummaryDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 AI 요약 요청 API"
        request { body<AiSummaryRequest>() }
        response {
            code(HttpStatusCode.Accepted) {
                description = "링크 AI 요약 요청 접수 성공"
                body<ApiResponse<AiSummaryResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 AI 요약 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 AI 요약 요청 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 AI 요약 요청 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_NOT_FOUND)
                }
            }
        }
    }

internal fun getLinkDetailDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 상세 조회 API"
        request {
            pathParameter<Long>("id") { description = "링크 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 상세 조회 성공"
                body<ApiResponse<GetLinkDetailResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 상세 조회 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 상세 조회 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_NOT_FOUND)
                }
            }
        }
    }

internal fun getLinksDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 검색 API"
        request {
            queryParameter<String?>("query") { description = "검색어" }
            queryParameter<Long?>("folderId") { description = "폴더 ID 필터" }
            queryParameter<String>("order") { description = "정렬 기준 (latest / earliest / laxico / similar), 기본값: latest" }
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 15" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 검색 성공"
                body<ApiResponse<ScrollResponse<GetLinksContentResponse>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 검색 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "링크 검색에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_OWNER_NOT_FOUND)
                }
            }
        }
    }

internal fun updateLinkDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 수정 API"
        request {
            pathParameter<Long>("id") { description = "링크 ID" }
            body<UpdateLinkRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 수정 성공"
                body<ApiResponse<UpdateLinkResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 수정 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_URL_BLANK,
                        ErrorCode.LINK_URL_WRONG_FORMAT,
                        ErrorCode.LINK_URL_NOT_HTTP,
                        ErrorCode.LINK_URL_WRONG_HOST,
                        ErrorCode.LINK_TITLE_BLANK,
                        ErrorCode.LINK_TITLE_OVER_MAX,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_DIFFERENT_OWNER,
                        ErrorCode.FOLDER_DIFFERENT_OWNER,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 수정에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_NOT_FOUND,
                        ErrorCode.LINK_FOLDER_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun patchLinkDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 부분 수정 API"
        request {
            pathParameter<Long>("id") { description = "링크 ID" }
            body<PatchLinkRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 부분 수정 성공"
                body<ApiResponse<PatchLinkResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 부분 수정 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.TODO_TITLE_BLANK,
                        ErrorCode.TODO_TITLE_OVER_MAX,
                        ErrorCode.TODO_DIFFERENT_LINK,
                        ErrorCode.TODO_DUPLICATE_ID,
                        ErrorCode.TODO_REPEAT_FIELDS_INCOMPLETE,
                        ErrorCode.TODO_REPEAT_DAYS_EMPTY,
                        ErrorCode.TODO_REPEAT_TIME_INVALID,
                        ErrorCode.TODO_REPEAT_TIMEZONE_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 부분 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_DIFFERENT_OWNER,
                        ErrorCode.FOLDER_DIFFERENT_OWNER,
                        ErrorCode.TODO_DIFFERENT_OWNER,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 부분 수정에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_NOT_FOUND,
                        ErrorCode.LINK_FOLDER_NOT_FOUND,
                        ErrorCode.TODO_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun deleteLinkDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 삭제 API"
        request {
            pathParameter<Long>("id") { description = "링크 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 삭제 성공"
                body<EmptySuccessResponse>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 삭제 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 삭제 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_OWNER_NOT_FOUND)
                }
            }
        }
    }
