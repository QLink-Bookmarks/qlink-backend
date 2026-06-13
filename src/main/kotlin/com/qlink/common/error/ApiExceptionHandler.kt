package com.qlink.common.error

import com.qlink.common.response.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger("com.qlink.api-error")

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
            logError(ErrorCode.COMMON_URL_NOT_FOUND, null)
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
}
