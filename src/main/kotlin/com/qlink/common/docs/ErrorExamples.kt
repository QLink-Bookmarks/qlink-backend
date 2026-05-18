package com.qlink.common.docs

import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.SimpleBodyConfig
import io.ktor.http.HttpStatusCode

fun ResponsesConfig.authErrorResponse() {
    code(HttpStatusCode.Unauthorized) {
        description = "인증 실패"
        body<ApiResponse<ErrorDetail>> {
            examples(
                ErrorCode.AUTH_NO_CREDENTIALS,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                ErrorCode.AUTH_WRONG_CREDENTIALS,
                ErrorCode.AUTH_UNEXPECTED_CREDENTIALS,
            )
        }
    }
}

fun SimpleBodyConfig.examples(vararg errorCodes: ErrorCode) {
    errorCodes.forEach { errorCode ->
        example(errorCode.code) {
            description = errorCode.message
            value =
                ApiResponse(
                    success = false,
                    error =
                        ErrorDetail(
                            code = errorCode.code,
                            message = errorCode.message,
                        ),
                )
        }
    }
}
