package com.qlink.di

import com.qlink.common.boolean
import com.qlink.common.int
import com.qlink.common.optionalStringList
import com.qlink.common.string
import com.qlink.common.stringList
import com.qlink.config.CorsConfig
import com.qlink.config.HttpLoggingConfig
import com.qlink.config.HttpPluginConfig
import com.qlink.config.MonitoringConfig
import com.qlink.config.SecurityConfig
import io.ktor.server.config.ApplicationConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module

fun pluginModule(config: ApplicationConfig) = module {
  single {
    HttpPluginConfig(
      cors = CorsConfig(
        methods = config.stringList("http.cors.methods"),
        headers = config.stringList("http.cors.headers"),
        anyHost = config.boolean("http.cors.anyHost"),
        hosts = config.optionalStringList("http.cors.hosts"),
      ),
      defaultHeaders = mapOf(
        config.string("http.defaultHeaders.engine.name") to
          config.string("http.defaultHeaders.engine.value"),
      ),
      openApiPath = config.string("http.documentation.openApiPath"),
      swaggerPath = config.string("http.documentation.swaggerPath"),
    )
  }

  single {
    MonitoringConfig(
      metricsPath = config.string("monitoring.metricsPath"),
      httpLogging = HttpLoggingConfig(
        slowThresholdMs = config.int("logging.http.slowThresholdMs").toLong(),
        maxLength = config.int("logging.http.maxLength"),
        traceIdLength = config.int("logging.http.traceIdLength"),
        sensitiveKeys = config.stringList("logging.http.sensitiveKeys"),
      ),
    )
  }

  single {
    SecurityConfig(
      jwtAudience = config.string("security.jwt.audience"),
      jwtDomain = config.string("security.jwt.domain"),
      jwtRealm = config.string("security.jwt.realm"),
      jwtSecret = config.string("security.jwt.secret"),
    )
  }

  single {
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
  }
}
