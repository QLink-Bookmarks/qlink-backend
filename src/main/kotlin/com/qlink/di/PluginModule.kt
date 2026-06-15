package com.qlink.di

import com.qlink.common.error.ApiExceptionHandler
import com.qlink.config.AppleConfig
import com.qlink.config.CorsConfig
import com.qlink.config.DocumentationConfig
import com.qlink.config.HttpLoggingConfig
import com.qlink.config.HttpPluginConfig
import com.qlink.config.MonitoringConfig
import com.qlink.config.SecurityConfig
import com.qlink.config.boolean
import com.qlink.config.int
import com.qlink.config.optionalInt
import com.qlink.config.optionalStringList
import com.qlink.config.string
import com.qlink.config.stringList
import io.ktor.server.config.ApplicationConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module

fun pluginModule(config: ApplicationConfig) =
    module {
        single {
            HttpPluginConfig(
                cors =
                    CorsConfig(
                        methods = config.stringList("http.cors.methods"),
                        headers = config.stringList("http.cors.headers"),
                        anyHost = config.boolean("http.cors.anyHost"),
                        hosts = config.optionalStringList("http.cors.hosts"),
                        allowCredentials = config.boolean("http.cors.allowCredentials"),
                        allowNonSimpleContentTypes = config.boolean("http.cors.allowNonSimpleContentTypes"),
                    ),
                defaultHeaders =
                    mapOf(
                        config.string("http.defaultHeaders.engine.name") to
                            config.string("http.defaultHeaders.engine.value"),
                    ),
            )
        }

        single {
            DocumentationConfig(
                openApiPath = config.string("http.documentation.openApiPath"),
                swaggerPath = config.string("http.documentation.swaggerPath"),
                redocPath = config.string("http.documentation.redocPath"),
            )
        }

        single {
            MonitoringConfig(
                metricsPath = config.string("monitoring.metricsPath"),
                httpLogging =
                    HttpLoggingConfig(
                        slowThresholdMs = config.int("logging.http.slowThresholdMs").toLong(),
                        maxLength = config.int("logging.http.maxLength"),
                        traceIdLength = config.int("logging.http.traceIdLength"),
                        sensitiveKeys = config.stringList("logging.http.sensitiveKeys"),
                    ),
            )
        }

        single { ApiExceptionHandler() }

        single {
            SecurityConfig(
                jwtSecret = config.string("security.jwt.secret"),
                accessDurationSeconds = config.optionalInt("security.access.duration") ?: 900,
                refreshDurationSeconds = config.optionalInt("security.refresh.duration") ?: 1_209_600,
            )
        }

        single {
            AppleConfig.from(config)
        }

        single {
            PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        }
    }
