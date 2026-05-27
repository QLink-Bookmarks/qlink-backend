package com.qlink.folder.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.CreateFolderResponse
import com.qlink.folder.dto.GetFoldersContentResponse
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.dto.UpdateFolderResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getFoldersDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 목록 조회 API"
        request {
            queryParameter<String?>("query") { description = "검색어" }
            queryParameter<String>("order") { description = "정렬 기준 (latest / earliest / laxico / similar), 기본값: latest" }
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 15" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 목록 조회 성공"
                body<ApiResponse<ScrollResponse<GetFoldersContentResponse>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 목록 조회 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "폴더 목록 조회에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND)
                }
            }
        }
    }

internal fun createFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 생성 API"
        request { body<CreateFolderRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "폴더 생성 성공"
                body<ApiResponse<CreateFolderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 생성 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_NAME_BLANK,
                        ErrorCode.FOLDER_NAME_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "폴더 생성에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "같은 이름의 폴더가 이미 존재함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DUPLICATE_NAME)
                }
            }
        }
    }

internal fun updateFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 수정 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            body<UpdateFolderRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 수정 성공"
                body<ApiResponse<UpdateFolderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 수정 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_NAME_BLANK,
                        ErrorCode.FOLDER_NAME_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "폴더 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "폴더 수정 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_OWNER_NOT_FOUND,
                        ErrorCode.FOLDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "같은 이름의 폴더가 이미 존재함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DUPLICATE_NAME)
                }
            }
        }
    }

internal fun deleteFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 삭제 API"
        description = "onDelete는 대소문자를 구분하지 않고 `cascade` 또는 `null`을 받을 수 있고, 생략하면 `null`로 처리돼요."
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            queryParameter<String?>("onDelete") { description = "폴더 삭제 시 링크 처리 방법 (cascade: 링크도 삭제, null: 링크의 폴더 연결 해제), 기본값: null" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 삭제 성공"
                body<EmptySuccessResponse>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 삭제 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "폴더 삭제 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "폴더 삭제에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND)
                }
            }
        }
    }
