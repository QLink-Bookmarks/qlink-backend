package com.qlink.config

import java.net.URI

data class LinkPreviewUrlConfig(
    val publicBaseUrl: String,
    val environment: String,
) {
    init {
        val uri = URI(publicBaseUrl)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank())
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        require(environment in setOf("dev", "prod"))
    }

    companion object {
        fun from(
            s3Config: S3Config,
            env: Map<String, String> = System.getenv(),
        ): LinkPreviewUrlConfig =
            LinkPreviewUrlConfig(
                publicBaseUrl = requireNotNull(s3Config.publicBaseUrl) { "Preview CDN public base URL is not configured" },
                environment = env["APP_ENV"]?.lowercase() ?: "dev",
            )
    }
}
