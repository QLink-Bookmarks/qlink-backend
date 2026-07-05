package com.qlink.post.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.dto.CreatePostResponse
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
