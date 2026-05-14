package com.qlink.plugin

import com.qlink.common.response.ErrorDetail
import com.qlink.config.DocumentationConfig
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.OpenApiVersion
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorredoc.redoc
import io.github.smiley4.ktorswaggerui.config.OperationsSort
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

private const val BEARER_AUTH_SCHEME = "bearerAuth"

fun Application.configureDocs() {
  val config by inject<DocumentationConfig>()

  install(OpenApi) {
    info {
      title = "QLink API"
      version = "1.0.0"
    }
    outputFormat = OutputFormat.JSON
    openApiVersion = OpenApiVersion.V3_0
    schemas {
      generator = SchemaGenerator.kotlinx()
    }
    security {
      securityScheme(BEARER_AUTH_SCHEME) {
        type = AuthType.HTTP
        scheme = AuthScheme.BEARER
        bearerFormat = "JWT"
      }
      defaultSecuritySchemeNames(BEARER_AUTH_SCHEME)
      defaultUnauthorizedResponse {
        description = "Bearer token is missing or invalid."
        body<ErrorDetail>()
      }
    }
  }

  routing {
    route(config.openApiPath) {
      openApi()
    }
    route(config.swaggerPath) {
      swaggerUI("/${config.openApiPath}") {
        deepLinking = true
        operationsSorter = OperationsSort.HTTP_METHOD
        persistAuthorization = true
        tryItOutEnabled = true
      }
    }
    route(config.redocPath) {
      redoc("/${config.openApiPath}") {
        pageTitle = "QLink API Reference"
        disableSearch = false
        hideDownloadButton = false
        pathInMiddlePanel = true
        requiredPropsFirst = true
      }
    }
  }
}
