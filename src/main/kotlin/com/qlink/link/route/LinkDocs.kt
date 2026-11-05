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
import com.qlink.link.dto.CopyLinkRequest
import com.qlink.link.dto.CopyLinkResponse
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.dto.GetLinkDetailResponse
import com.qlink.link.dto.GetLinksContentResponse
import com.qlink.link.dto.PatchLinkRequest
import com.qlink.link.dto.PatchLinkResponse
import com.qlink.link.dto.SetLinkFavoriteRequest
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
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.LINK_URL_BLANK,
                        ErrorCode.LINK_URL_WRONG_FORMAT,
                        ErrorCode.LINK_URL_NOT_HTTP,
                        ErrorCode.LINK_URL_WRONG_HOST,
                        ErrorCode.LINK_TITLE_BLANK,
                        ErrorCode.LINK_TITLE_OVER_MAX,
                        ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED,
                        ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED,
                        ErrorCode.LINK_SOURCE_TYPE_NOT_ALLOWED,
                        ErrorCode.TODO_TITLE_BLANK,
                        ErrorCode.TODO_TITLE_OVER_MAX,
                        ErrorCode.TODO_REPEAT_FIELDS_INCOMPLETE,
                        ErrorCode.TODO_REPEAT_DAYS_EMPTY,
                        ErrorCode.TODO_REPEAT_TIME_INVALID,
                        ErrorCode.TODO_REPEAT_TIMEZONE_INVALID,
                        ErrorCode.TODO_REMINDER_AT_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 생성 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_FOLDER_ACCESS_DENIED)
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
            code(HttpStatusCode.OK) {
                description = "링크 AI 요약 요청 접수 성공"
                body<ApiResponse<AiSummaryResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "링크 AI 요약 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.LINK_URL_BLANK,
                        ErrorCode.LINK_URL_WRONG_FORMAT,
                        ErrorCode.LINK_URL_NOT_HTTP,
                        ErrorCode.LINK_URL_WRONG_HOST,
                        ErrorCode.LINK_TITLE_BLANK,
                        ErrorCode.LINK_TITLE_OVER_MAX,
                        ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED,
                        ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 AI 요약 요청 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_DIFFERENT_OWNER,
                        ErrorCode.AI_USER_PROVIDER_ACCESS_DENIED,
                        ErrorCode.LINK_FOLDER_ACCESS_DENIED,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 AI 요약 요청 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_NOT_FOUND,
                        ErrorCode.AI_USER_PROVIDER_NOT_FOUND,
                        ErrorCode.AI_MODEL_NOT_FOUND,
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_FOLDER_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun getLinkDetailDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 상세 조회 API"
        description =
            """
            content는 링크 본문의 발췌예요. 본문 기록이 없는(기능 이전) 링크는 null이에요.
            excerpt는 본문 수집이 끝났고 발췌를 만들 수 있을 때만 있어요. 본문이 100자 이상이면 앞 100자, 미만이면 약 30%에
            말줄임(…)을 붙여 101 UTF-16 유닛 이하예요. 앞부분이 비는 아주 짧은 본문(3자 이하, 이모지로 시작하는 4~6자)은 null이에요.
            isPending은 본문을 아직 수집 중일 때만 true예요(앱의 자동 재확인용, 화면 미표시). 수집 실패·차단은 { excerpt: null, isPending: false }예요.
            본문 전체와 수집 상태·실패 코드는 어떤 응답에도 없어요.
            """.trimIndent()
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
                    examples(ErrorCode.LINK_DIFFERENT_OWNER, ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED)
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
        description =
            """
            query가 있으면 제목·URL·태그·요약·메모와 수집된 본문을 찾아요. 앞뒤 공백을 지우고 NFC·소문자로 맞춘 뒤 찾으며,
            %·_·역슬래시는 와일드카드가 아니라 글자 그대로 찾아요. 1자 검색어는 본문을 찾지 않아요.
            정렬은 메타데이터(제목·URL·태그·요약·메모) 일치 링크가 먼저, 본문만 일치한 링크가 뒤이고 각 묶음 안에서 order를 따라요.
            query가 있을 때 발급된 커서에는 isMetadataMatched(similar는 contentScore도)가 있어야 해요. 이 기능 이전에 발급된 검색 커서는
            COMMON_CURSOR_FIELD_MISSING으로 거절되니 첫 페이지부터 다시 요청해요. query가 없으면 정렬·커서는 기존과 같아요.
            contentExcerpt는 본문 수집이 끝난 링크의 발췌(상세 content.excerpt와 같음)이고, 그 외에는 null이에요.
            matchedFields는 일치한 필드(TITLE / URL / TAGS / SUMMARY / MEMO / CONTENT)이며 query가 없으면 []예요(앱 내부 판단용).
            contentSnippet은 본문이 일치한 링크에만 있어요. text는 말줄임 포함 100 UTF-16 유닛 이하이고, highlights는 text 기준
            [start, end) 범위로 겹치지 않으며 만들 수 없으면 []예요.
            """.trimIndent()
        request {
            queryParameter<String?>("query") { description = "검색어 (1자면 본문 미검색, %·_·역슬래시는 글자 그대로)" }
            queryParameter<Long?>("folderId") { description = "폴더 ID 필터" }
            queryParameter<Boolean?>("isFavorite") { description = "바로가기 필터 (true: 바로가기만, false: 일반만, 미지정: 전체)" }
            queryParameter<String>("order") {
                description = "정렬 기준 (latest / earliest: 생성 시각(createdAt) 기준, 같으면 id / laxico / similar), 기본값: latest"
            }
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
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.LINK_URL_BLANK,
                        ErrorCode.LINK_URL_WRONG_FORMAT,
                        ErrorCode.LINK_URL_NOT_HTTP,
                        ErrorCode.LINK_URL_WRONG_HOST,
                        ErrorCode.LINK_TITLE_BLANK,
                        ErrorCode.LINK_TITLE_OVER_MAX,
                        ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED,
                        ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_DIFFERENT_OWNER,
                        ErrorCode.LINK_FOLDER_ACCESS_DENIED,
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
                        ErrorCode.LINK_FOLDER_ACCESS_DENIED,
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

internal fun setLinkFavoriteDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 바로가기 설정 API"
        request {
            pathParameter<Long>("id") { description = "링크 ID" }
            body<SetLinkFavoriteRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "링크 바로가기 설정 성공"
                body<EmptySuccessResponse>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "링크 바로가기 설정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "링크 바로가기 설정 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_NOT_FOUND,
                    )
                }
            }
        }
    }

internal fun copyLinkDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 폴더 링크 복사 API"
        request {
            pathParameter<Long>("id") { description = "복사할 링크 ID" }
            body<CopyLinkRequest>()
        }
        response {
            code(HttpStatusCode.Created) {
                description = "공유 폴더 링크 복사 성공"
                body<ApiResponse<CopyLinkResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "공유 폴더 링크 복사 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_COPY_FOLDER_MISMATCH)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "공유 폴더 링크 복사 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED,
                        ErrorCode.FOLDER_DIFFERENT_OWNER,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "공유 폴더 링크 복사에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.LINK_OWNER_NOT_FOUND,
                        ErrorCode.LINK_SHARE_FOLDER_NOT_FOUND,
                        ErrorCode.LINK_TARGET_FOLDER_NOT_FOUND,
                        ErrorCode.LINK_NOT_FOUND,
                        ErrorCode.LINK_COPY_LINK_FOLDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더 링크 복사 대상이 공유 폴더가 아님"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_COPY_NOT_SHARED_FOLDER)
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
