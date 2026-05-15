package com.qlink.config

data class HttpPluginConfig(
    val cors: CorsConfig,
    val defaultHeaders: Map<String, String>,
)

data class DocumentationConfig(
    val openApiPath: String,
    val swaggerPath: String,
    val redocPath: String,
)

data class CorsConfig(
    val methods: List<String>,
    val headers: List<String>,
    val anyHost: Boolean,
    val hosts: List<String>,
)
