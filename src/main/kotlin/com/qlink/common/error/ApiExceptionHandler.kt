package com.qlink.common.error

import com.qlink.common.response.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.path
import kotlinx.coroutines.CancellationException
import org.slf4j.Logger

class ApiExceptionHandler(
    private val log: Logger,
) {
    val exceptionHandlerConfig: StatusPagesConfig.() -> Unit = {
        exception<BusinessException> { call, cause ->
            logError(cause.errorCode, cause)
            call.respondError(cause.errorCode, cause.cause)
        }

        exception<BadRequestException> { call, cause ->
            logError(ErrorCode.COMMON_BAD_REQUEST, cause)
            call.respondError(ErrorCode.COMMON_BAD_REQUEST, cause.cause)
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            logUrlNotFound(call.request.path())
            call.respondError(
                errorCode = ErrorCode.COMMON_URL_NOT_FOUND,
            )
        }

        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }

            logError(ErrorCode.COMMON_INTERNAL_SERVER_ERROR, cause)
            call.respondError(
                errorCode = ErrorCode.COMMON_INTERNAL_SERVER_ERROR,
                cause = cause,
            )
        }
    }

    private fun logError(
        errorCode: ErrorCode,
        cause: Throwable?,
    ) {
        val format = "[API_ERROR] code={} status={} message={} cause={} causeMessage={}"
        val causeName = cause?.javaClass?.name
        val causeMessage = cause?.message
        if (errorCode.status >= HttpStatusCode.InternalServerError.value) {
            log.error(format, errorCode.code, errorCode.status, errorCode.message, causeName, causeMessage, cause)
        } else {
            log.warn(format, errorCode.code, errorCode.status, errorCode.message, causeName, causeMessage)
        }
    }

    private fun logUrlNotFound(path: String) {
        ErrorCode.COMMON_URL_NOT_FOUND.let { errorCode ->
            log.warn("[API_ERROR] code={} status={} message={} path={}", errorCode.code, errorCode.status, errorCode.message, path)
        }
    }
}
