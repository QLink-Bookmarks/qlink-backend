package com.qlink.common.response

import com.qlink.common.error.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: T? = null,
)

@Serializable
data class EmptySuccessResponse(
    val success: Boolean,
    val data: Unit?,
)

suspend inline fun <reified T> ApplicationCall.respondSuccess(
    status: HttpStatusCode,
    data: T,
) {
    respond(
        status = status,
        message =
            ApiResponse(
                success = true,
                data = data,
            ),
    )
}

suspend fun ApplicationCall.respondSuccess(status: HttpStatusCode) {
    respond(
        status = status,
        message =
            EmptySuccessResponse(
                success = true,
                data = null,
            ),
    )
}

suspend fun ApplicationCall.respondError(
    errorCode: ErrorCode,
    cause: Throwable? = null,
    causeName: String? = null,
    causeMessage: String? = null,
) {
    respond(
        status = HttpStatusCode.fromValue(errorCode.status),
        message =
            ApiResponse(
                success = false,
                error =
                    ErrorDetail(
                        code = errorCode.code,
                        message = errorCode.message,
                        cause = cause?.javaClass?.name ?: causeName,
                        causeMessage = cause?.message ?: causeMessage,
                    ),
            ),
    )
}
