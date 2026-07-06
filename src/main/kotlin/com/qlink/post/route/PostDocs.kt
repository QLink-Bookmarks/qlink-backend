package com.qlink.post.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.dto.CreatePostResponse
import com.qlink.post.dto.GetPostResponse
import com.qlink.post.dto.GetPostsContentResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun createPostDocs(): RouteConfig.() -> Unit =
    {
        summary = "게시글 생성 API"
        description = "게시글을 생성합니다. type=ANNOUNCEMENT면 ADMIN/SUPER_ADMIN만 가능하며 전 사용자에게 알림을 발송합니다."
        request {
            body<CreatePostRequest>()
        }
        response {
            code(HttpStatusCode.Created) {
                description = "게시글 생성 성공"
                body<ApiResponse<CreatePostResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "게시글 생성 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.POST_TITLE_BLANK,
                        ErrorCode.POST_TITLE_OVER_MAX,
                        ErrorCode.POST_CONTENTS_BLANK,
                        ErrorCode.POST_TYPE_NOT_SUPPORTED,
                    )
                }
            }
            code(HttpStatusCode.Forbidden) {
                description = "공지사항 관리 권한 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.POST_ANNOUNCEMENT_FORBIDDEN)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "작성자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.POST_AUTHOR_NOT_FOUND)
                }
            }
        }
    }

internal fun getPostsDocs(): RouteConfig.() -> Unit =
    {
        summary = "게시글 목록 조회 API"
        description = "타입 기본값 ANNOUNCEMENT(대소문자 무관). type=FEEDBACK 은 ADMIN/SUPER_ADMIN 만 조회 가능."
        request {
            queryParameter<String?>("query") { description = "제목 검색어" }
            queryParameter<String?>("type") { description = "게시글 타입(ANNOUNCEMENT, FEEDBACK), 기본값 ANNOUNCEMENT" }
            queryParameter<String>("order") { description = "정렬 기준, 기본값: latest" }
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 15" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "게시글 목록 조회 성공"
                body<ApiResponse<ScrollResponse<GetPostsContentResponse>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "게시글 목록 조회 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.POST_TYPE_NOT_SUPPORTED,
                        ErrorCode.COMMON_INVALID_SORT_ORDER,
                        ErrorCode.COMMON_CURSOR_MALFORMED,
                        ErrorCode.COMMON_CURSOR_ORDER_MISMATCH,
                        ErrorCode.COMMON_CURSOR_FIELD_MISSING,
                    )
                }
            }
            code(HttpStatusCode.Forbidden) {
                description = "피드백 조회 권한 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.POST_FEEDBACK_FORBIDDEN)
                }
            }
        }
    }

internal fun getPostDocs(): RouteConfig.() -> Unit =
    {
        summary = "게시글 상세 조회 API"
        description = "게시글 전체 필드를 반환합니다. FEEDBACK 은 ADMIN/SUPER_ADMIN 만 조회 가능."
        request {
            pathParameter<Long>("id") { description = "게시글 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "게시글 상세 조회 성공"
                body<ApiResponse<GetPostResponse>>()
            }
            code(HttpStatusCode.Forbidden) {
                description = "피드백 조회 권한 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.POST_FEEDBACK_FORBIDDEN)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "게시글 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.POST_NOT_FOUND)
                }
            }
        }
    }
