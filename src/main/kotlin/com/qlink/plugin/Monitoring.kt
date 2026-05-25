package com.qlink.plugin

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.config.MonitoringConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureMonitoring() {
    val config by inject<MonitoringConfig>()
    val appMicrometerRegistry by inject<PrometheusMeterRegistry>()
    val httpLogger = LoggerFactory.getLogger("com.qlink.http")

    install(CallLogging) {
        logger = httpLogger
        level = Level.INFO
        filter { call ->
            call.request.path().startsWith("/api")
        }
        mdc("traceId") { call ->
            call.traceId(config.httpLogging.traceIdLength)
        }
        format { call ->
            val query =
                LogSanitizer.sanitize(
                    value = call.request.queryString().dashIfBlank(),
                    sensitiveKeys = config.httpLogging.sensitiveKeys,
                    maxLength = config.httpLogging.maxLength,
                )
            val contentLength = call.request.header(HttpHeaders.ContentLength).dashIfBlank()
            val durationMs = call.processingTimeMillis()
            val status = call.response.status() ?: HttpStatusCode.OK
            val event =
                if (durationMs >= config.httpLogging.slowThresholdMs) {
                    "[HTTP-SLOW]"
                } else {
                    "[HTTP]"
                }

            "$event method=${call.request.httpMethod.value} " +
                "uri=${call.request.path().ifEmpty { "/" }}, " +
                "query=$query, " +
                "contentLength=$contentLength, " +
                "status=${status.value}, " +
                "durationMs=$durationMs, " +
                "userId=${call.userId()}, " +
                "user-agent=${call.request.header(HttpHeaders.UserAgent).orEmpty()}"
        }
    }

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // ...
    }

    routing {
        get(config.metricsPath) {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}

private val TraceIdKey = AttributeKey<String>("TraceId")

private fun ApplicationCall.traceId(length: Int): String {
    attributes.getOrNull(TraceIdKey)?.let { return it }

    val traceId =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(length.coerceIn(1, 32))

    attributes.put(TraceIdKey, traceId)
    return traceId
}

private fun ApplicationCall.userId(): String = principal<JwtPrincipal>()?.userId?.toString() ?: "anonymous"

private fun String?.dashIfBlank(): String = if (isNullOrBlank()) "-" else this
