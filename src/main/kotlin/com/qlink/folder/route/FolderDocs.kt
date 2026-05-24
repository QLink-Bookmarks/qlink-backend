package com.qlink.folder.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.CreateFolderResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

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
