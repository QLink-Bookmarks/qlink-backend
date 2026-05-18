package com.qlink.link.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
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
