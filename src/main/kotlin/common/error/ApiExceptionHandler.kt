package com.qlink.common.error

import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

class ApiExceptionHandler {
    val exceptionHandlerConfig: StatusPagesConfig.() -> Unit = {
        exception<BusinessException> { call, cause ->
            call.respondError(cause.errorCode)
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(
                errorCode = ErrorCode.INT_404_0001,
            )
        }

        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }

            call.respondError(
                errorCode = ErrorCode.INT_500_0001,
                cause = cause,
            )
        }
    }
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
            ApiResponse<String>(
                success = false,
                error =
                    ErrorDetail(
                        code = errorCode.name,
                        message = errorCode.message,
                        cause = cause?.javaClass?.name ?: causeName,
                        causeMessage = cause?.message ?: causeMessage,
                    ),
            ),
    )
}
