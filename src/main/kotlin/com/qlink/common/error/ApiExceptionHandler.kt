package com.qlink.common.error

import com.qlink.common.response.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.util.rootCause
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    val exceptionHandlerConfig: StatusPagesConfig.() -> Unit = {
        exception<BusinessException> { call, cause ->
            call.respondError(cause.errorCode, cause.cause)
        }

        exception<BadRequestException> { call, cause ->
            call.respondError(ErrorCode.COMMON_BAD_REQUEST, cause.cause)
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(
                errorCode = ErrorCode.COMMON_URL_NOT_FOUND,
            )
        }

        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }

            log.error(cause.message ?: "Unknown Exception happened - ", cause)

            call.respondError(
                errorCode = ErrorCode.COMMON_INTERNAL_SERVER_ERROR,
                cause = cause,
            )
        }
    }
}
