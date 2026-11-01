package com.qlink.image.route

import com.qlink.common.docs.authErrorResponse
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
            "`purpose`로 저장 대상을 골라요. 생략하면 `PROFILE`이에요. " +
            "`PROFILE` / `POST` / `LINK_PREVIEW`는 multipart/form-data 의 `image` 파트로 이미지 파일을 전달하면 " +
            "실제 형식을 확인해 저장하고 CDN URL을 응답해요. JPEG / PNG / WebP / GIF 형식, 최대 10MB까지 지원해요. " +
            "`FAVICON`은 파일 없이 `url`에 원본 페이지 주소를 전달하면 그 페이지의 파비콘을 저장하고 CDN URL을 응답해요."
        request {
            queryParameter<String?>("purpose") {
                description = "저장 대상(PROFILE, POST, LINK_PREVIEW, FAVICON), 대소문자 무관, 기본값 PROFILE"
            }
            queryParameter<String?>("url") {
                description = "파비콘을 찾을 원본 페이지의 절대 HTTP(S) URL. FAVICON일 때만 필수이고 다른 용도에서는 보내지 않아요"
            }
        }
        response {
            code(HttpStatusCode.Created) {
                description = "이미지 업로드 성공"
                body<ApiResponse<UploadImageResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "요청 또는 이미지 파일 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.IMAGE_PURPOSE_NOT_SUPPORTED,
                        ErrorCode.IMAGE_FILE_NOT_ALLOWED,
                        ErrorCode.IMAGE_URL_INVALID,
                        ErrorCode.IMAGE_FILE_REQUIRED,
                        ErrorCode.IMAGE_INVALID_FORMAT,
                        ErrorCode.IMAGE_FILE_TOO_LARGE,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "로그인 사용자 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.IMAGE_OWNER_NOT_FOUND)
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
