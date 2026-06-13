package com.qlink.plugin

import com.qlink.config.HttpLoggingConfig
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.formUrlEncode
import io.ktor.http.takeFrom
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

class ExternalHttpClientLoggingConfig {
    lateinit var httpLogging: HttpLoggingConfig
}

val ExternalHttpClientLogging =
    createClientPlugin("ExternalHttpClientLogging", ::ExternalHttpClientLoggingConfig) {
        val httpLogging = pluginConfig.httpLogging
        val log = LoggerFactory.getLogger("com.qlink.external-http")

        on(Send) { request ->
            val method = request.method.value
            val url = request.urlWithoutQuery()
            val query =
                request.queryWithoutKey().sanitizeExternalHttpLog(httpLogging)
            val startedAt = TimeSource.Monotonic.markNow()

            log.info("[EXTERNAL_HTTP_REQUEST] method={} url={} query={}", method, url, query)

            val call =
                runCatching { proceed(request).save() }
                    .getOrElse {
                        log.warn(
                            "[EXTERNAL_HTTP_RESPONSE] method={} url={} status={} durationMs={} response={}",
                            method,
                            url,
                            "FAILED",
                            startedAt.elapsedNow().inWholeMilliseconds,
                            it.message,
                            it,
                        )
                        throw it
                    }
            val response = call.response.bodyAsText()

            log.info(
                "[EXTERNAL_HTTP_RESPONSE] method={} url={} status={} durationMs={} response={}",
                method,
                url,
                call.response.status.value,
                startedAt.elapsedNow().inWholeMilliseconds,
                response.sanitizeExternalHttpLog(httpLogging).toSingleLine(),
            )

            call
        }
    }

private fun HttpRequestBuilder.urlWithoutQuery(): String =
    URLBuilder()
        .takeFrom(url)
        .apply {
            parameters.clear()
            encodedParameters.clear()
        }.buildString()

private fun HttpRequestBuilder.queryWithoutKey(): String {
    val parameters =
        Parameters.build {
            url.parameters
                .names()
                .filterNot { it.equals(API_KEY_QUERY_PARAMETER, ignoreCase = true) }
                .forEach { name ->
                    url.parameters.getAll(name).orEmpty().forEach { value ->
                        append(name, value)
                    }
                }
        }

    return parameters.formUrlEncode().ifBlank { "-" }
}

private fun String.toSingleLine(): String = replace(Regex("""\s*[\r\n]+\s*"""), " ").trim()

private fun String.sanitizeExternalHttpLog(httpLogging: HttpLoggingConfig): String =
    LogSanitizer
        .sanitize(
            value = this,
            sensitiveKeys = httpLogging.sensitiveKeys,
            maxLength = Int.MAX_VALUE,
        ).removeApiKeyValues()

private fun String.removeApiKeyValues(): String =
    removeApiKeyQueryValues()
        .replace(Regex("""(?i)(,?\s*"key"\s*:\s*"[^"]*"\s*,?)""")) { match ->
            when {
                match.value.startsWith(",") && match.value.endsWith(",") -> ","
                else -> ""
            }
        }.replace(Regex("""\{\s*,"""), "{")
        .replace(Regex(""",\s*}"""), "}")

private fun String.removeApiKeyQueryValues(): String =
    replace(Regex("""(?i)&$API_KEY_QUERY_PARAMETER=[^&"\s]*"""), "")
        .replace(Regex("""(?i)\?$API_KEY_QUERY_PARAMETER=[^&"\s]*&"""), "?")
        .replace(Regex("""(?i)\?$API_KEY_QUERY_PARAMETER=[^&"\s]*"""), "")

private const val API_KEY_QUERY_PARAMETER = "key"
