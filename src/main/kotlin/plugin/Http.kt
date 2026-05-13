package com.qlink.plugin

import com.qlink.config.HttpPluginConfig
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureHttp() {
  val config by inject<HttpPluginConfig>()

  install(CORS) {
    config.cors.methods.forEach { method ->
      allowMethod(HttpMethod(method))
    }
    config.cors.headers.forEach { header ->
      allowHeader(header)
    }
    if (config.cors.anyHost) {
      anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    } else {
      config.cors.hosts.forEach { host ->
        allowHost(host)
      }
    }
  }
  install(Compression)
  install(DefaultHeaders) {
    config.defaultHeaders.forEach { (name, value) ->
      header(name, value)
    }
  }
  routing {
    openAPI(path = config.openApiPath) {
            /*
             Documentation source configuration goes here.

             This can be from file (documentation.yaml), or it can be served dynamically from your sources using the
             `describe {}` API on routes.  When `openApi` enabled in Gradle, these calls will be automatically injected
             based on your code and comments.
             */
    }
  }
  routing {
    swaggerUI(path = config.swaggerPath) {
            /*
             Documentation source configuration goes here.

             This can be from file (documentation.yaml), or it can be served dynamically from your sources using the
             `describe {}` API on routes.  When `openApi` enabled in Gradle, these calls will be automatically injected
             based on your code and comments.
             */
    }
  }
}
