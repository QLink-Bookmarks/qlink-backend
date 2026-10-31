package com.qlink.link.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.link.dto.CreateLinkPreviewRequest
import com.qlink.link.dto.CreateLinkPreviewResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun createLinkPreviewDocs(): RouteConfig.() -> Unit =
    {
        summary = "링크 프리뷰 추출 API"
        description = "URL에서 제목·대표 이미지·파비콘·영상 여부를 추출해 반환해요. 이미지 추출 실패는 빈 프리뷰로 응답해요."
        request { body<CreateLinkPreviewRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "프리뷰 추출 완료"
                body<ApiResponse<CreateLinkPreviewResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "요청 URL 오류"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST, ErrorCode.LINK_PREVIEW_URL_INVALID)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.LINK_PREVIEW_OWNER_NOT_FOUND)
                }
            }
        }
    }
