package com.qlink.bookmarkimport.route

import com.qlink.bookmarkimport.dto.CreateImportPreviewRequest
import com.qlink.bookmarkimport.dto.CreateImportPreviewResponse
import com.qlink.bookmarkimport.dto.CreateImportRequest
import com.qlink.bookmarkimport.dto.CreateImportReversalRequest
import com.qlink.bookmarkimport.dto.GetImportDetailResponse
import com.qlink.bookmarkimport.dto.GetImportReversalPreviewResponse
import com.qlink.bookmarkimport.dto.ImportSummary
import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun createImportPreviewDocs(): RouteConfig.() -> Unit =
    {
        summary = "북마크 가져오기 미리보기 API"
        description =
            "앱이 파싱한 폴더·링크를 내 폴더·링크와 비교해요. 아무것도 저장하지 않아요. " +
            "폴더 이름은 앞뒤 공백을 빼고 NFC로 비교하고, 링크는 정규화한 URL로 비교해요."
        request { body<CreateImportPreviewRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "가져오기 미리보기 성공"
                body<ApiResponse<CreateImportPreviewResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "가져오기 미리보기 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMPORT_LINKS_EMPTY,
                        ErrorCode.IMPORT_LINKS_OVER_MAX,
                        ErrorCode.IMPORT_FOLDERS_OVER_MAX,
                        ErrorCode.IMPORT_KEY_DUPLICATED,
                        ErrorCode.IMPORT_FOLDER_NAME_BLANK,
                        ErrorCode.IMPORT_FOLDER_NAME_OVER_MAX,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기 미리보기에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMPORT_PREVIEW_OWNER_NOT_FOUND)
                }
            }
        }
    }

internal fun createImportDocs(): RouteConfig.() -> Unit =
    {
        summary = "북마크 가져오기 API"
        description =
            "폴더·링크를 요청 안에서 바로 저장하고 가져오기 요약을 돌려줘요. " +
            "같은 clientRequestId로 다시 보내면 새로 저장하지 않고 처음 기록의 요약을 돌려줘요. " +
            "검증에 실패한 링크는 실패로 기록하고, 이미 저장된 링크와 요청 안의 중복은 건너뛰어요. " +
            "모든 링크를 건너뛰어 만든 링크도 실패도 없으면 기록을 남기지 않고 거절해요."
        request { body<CreateImportRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "가져오기 성공"
                body<ApiResponse<ImportSummary>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "가져오기 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.IMPORT_LINKS_EMPTY,
                        ErrorCode.IMPORT_LINKS_OVER_MAX,
                        ErrorCode.IMPORT_FOLDERS_OVER_MAX,
                        ErrorCode.IMPORT_KEY_DUPLICATED,
                        ErrorCode.IMPORT_FILE_NAME_BLANK,
                        ErrorCode.IMPORT_FILE_NAME_OVER_MAX,
                        ErrorCode.IMPORT_FOLDER_NAME_BLANK,
                        ErrorCode.IMPORT_FOLDER_NAME_OVER_MAX,
                        ErrorCode.IMPORT_FOLDER_KEY_NOT_FOUND,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMPORT_OWNER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "모든 링크를 건너뛰어 새로 가져올 링크가 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMPORT_NOTHING_TO_IMPORT)
                }
            }
        }
    }

internal fun getImportsDocs(): RouteConfig.() -> Unit =
    {
        summary = "가져오기 기록 목록 조회 API"
        description = "내 가져오기 기록을 생성 시각(createdAt) 내림차순, 같으면 id 내림차순으로 돌려줘요."
        request {
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 15" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "가져오기 기록 목록 조회 성공"
                body<ApiResponse<ScrollResponse<ImportSummary>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "가져오기 기록 목록 조회 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_CURSOR_MALFORMED,
                        ErrorCode.COMMON_CURSOR_FIELD_MISSING,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기 기록 목록 조회에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMPORT_OWNER_NOT_FOUND)
                }
            }
        }
    }

internal fun getImportDetailDocs(): RouteConfig.() -> Unit =
    {
        summary = "가져오기 기록 상세 조회 API"
        description = "가져올 때 들어간 폴더별로 묶은 링크와 실패 항목을 돌려줘요. 미분류 묶음은 kind가 UNFILED이고 folderId·name이 null이에요."
        request {
            pathParameter<Long>("id") { description = "가져오기 기록 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "가져오기 기록 상세 조회 성공"
                body<ApiResponse<GetImportDetailResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기 기록 상세 조회 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMPORT_OWNER_NOT_FOUND,
                        ErrorCode.IMPORT_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun getImportReversalPreviewDocs(): RouteConfig.() -> Unit =
    {
        summary = "가져오기 되돌리기 미리보기 API"
        description =
            "가져온 뒤의 수정·삭제·추가·공유를 반영한 폴더와 링크의 상태를 돌려줘요. " +
            "링크는 지금 들어 있는 폴더 기준으로 묶고, 지워진 링크만 가져올 때의 폴더 아래에 둬요."
        request {
            pathParameter<Long>("id") { description = "가져오기 기록 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "가져오기 되돌리기 미리보기 성공"
                body<ApiResponse<GetImportReversalPreviewResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기 되돌리기 미리보기 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMPORT_OWNER_NOT_FOUND,
                        ErrorCode.IMPORT_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun createImportReversalDocs(): RouteConfig.() -> Unit =
    {
        summary = "가져오기 되돌리기 API"
        description =
            "고른 링크와 폴더를 지우고 갱신된 가져오기 요약을 돌려줘요. 지우는 폴더 안에 남는 링크는 미분류로 옮겨요. " +
            "이미 지워졌거나 되돌린 항목은 건너뛰어서, 같은 요청을 다시 보내도 결과가 같아요."
        request {
            pathParameter<Long>("id") { description = "가져오기 기록 ID" }
            body<CreateImportReversalRequest>()
        }
        response {
            code(HttpStatusCode.Created) {
                description = "가져오기 되돌리기 성공"
                body<ApiResponse<ImportSummary>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "가져오기 되돌리기 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMPORT_REVERSAL_ITEMS_EMPTY,
                        ErrorCode.IMPORT_REVERSAL_ITEM_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "가져오기 되돌리기 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMPORT_OWNER_NOT_FOUND,
                        ErrorCode.IMPORT_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 중인 폴더는 되돌릴 수 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED)
                }
            }
        }
    }
