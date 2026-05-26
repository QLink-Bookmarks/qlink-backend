package com.qlink.plugin

import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
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
private const val API_PREFIX = "api"

private val apiTags =
    mapOf(
        "links" to "링크 API",
        "users" to "유저 API",
        "folders" to "폴더 API",
        "todos" to "투두 API",
    )

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
        tags {
            apiTags.values.forEach { tag ->
                tag(tag) {
                    description = tag
                }
            }
            tagGenerator = { url ->
                val resource = url.dropWhile { it == API_PREFIX }.firstOrNull()
                listOfNotNull(apiTags[resource])
            }
        }
        security {
            securityScheme(BEARER_AUTH_SCHEME) {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
            }
            defaultSecuritySchemeNames(BEARER_AUTH_SCHEME)
            defaultUnauthorizedResponse {
                description = "인증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.AUTH_NO_CREDENTIALS,
                        ErrorCode.AUTH_INVALID_CREDENTIALS,
                        ErrorCode.AUTH_WRONG_CREDENTIALS,
                        ErrorCode.AUTH_UNEXPECTED_CREDENTIALS,
                    )
                }
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
