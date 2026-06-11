package com.qlink.image.route

import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.image.dto.UploadImageResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun uploadImageDocs(): RouteConfig.() -> Unit =
    {
        summary = "이미지 업로드 API"
        description =
            "multipart/form-data 의 `image` 파트로 이미지 파일을 전달하면 S3에 업로드하고 접근 URL을 응답해요. " +
            "JPEG / PNG / WebP / GIF 형식, 최대 10MB까지 지원해요."
        response {
            code(HttpStatusCode.Created) {
                description = "이미지 업로드 성공"
                body<ApiResponse<UploadImageResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "이미지 파일 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.IMAGE_FILE_REQUIRED,
                        ErrorCode.IMAGE_INVALID_FORMAT,
                        ErrorCode.IMAGE_FILE_TOO_LARGE,
                    )
                }
            }
            code(HttpStatusCode.InternalServerError) {
                description = "이미지 업로드 처리 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMAGE_UPLOAD_FAILED)
                }
            }
        }
    }
