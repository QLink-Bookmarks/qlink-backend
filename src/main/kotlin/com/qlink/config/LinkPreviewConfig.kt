package com.qlink.config

import io.ktor.server.config.ApplicationConfig

data class LinkPreviewConfig(
    val environment: String,
    val functionName: String,
    val publicBaseUrl: String,
    val region: String,
    val endpoint: String?,
) {
    init {
        require(environment == "dev" || environment == "prod")
        require(functionName.isNotBlank())
        require(publicBaseUrl.startsWith("https://") || publicBaseUrl.startsWith("http://localhost"))
    }

    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): LinkPreviewConfig =
            LinkPreviewConfig(
                environment = (env["APP_ENV"] ?: "dev").lowercase(),
                functionName = env["LINK_PREVIEW_LAMBDA_FUNCTION"] ?: config.string("linkPreview.lambdaFunction"),
                publicBaseUrl = env["LINK_PREVIEW_PUBLIC_BASE_URL"] ?: config.string("linkPreview.publicBaseUrl"),
                region = env["AWS_REGION"] ?: config.string("aws.s3.region"),
                endpoint =
                    if (env.containsKey("LINK_PREVIEW_LAMBDA_ENDPOINT")) {
                        env["LINK_PREVIEW_LAMBDA_ENDPOINT"]?.takeIf(String::isNotBlank)
                    } else {
                        config.optionalString("linkPreview.lambdaEndpoint")?.takeIf(String::isNotBlank)
                    },
            )
    }
}
