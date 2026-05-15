package com.qlink.plugin

import com.qlink.common.error.ApiExceptionHandler
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
import org.koin.ktor.ext.inject

fun Application.configureStatusPages() {
    val apiExceptionHandler by inject<ApiExceptionHandler>()

    install(StatusPages, apiExceptionHandler.exceptionHandlerConfig)
}
