package com.qlink.plugin

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.ErrorDetail
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

fun Application.configureStatusPages() {
  install(StatusPages) {
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

private suspend fun ApplicationCall.respondError(errorCode: ErrorCode, cause: Throwable? = null) {
  respond(
    status = HttpStatusCode.fromValue(errorCode.status),
    message = ApiResponse<String>(
      success = false,
      error = ErrorDetail(
        code = errorCode.name,
        message = errorCode.message,
        cause = cause?.javaClass?.name,
        causeMessage = cause?.message,
      ),
    ),
  )
}
