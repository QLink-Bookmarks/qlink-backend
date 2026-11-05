package com.qlink.config

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import io.ktor.server.config.ApplicationConfig

data class LinkPreviewConfig(
    val functionName: String,
    val region: String,
    val endpoint: String?,
) {
    init {
        functionName.isNotBlank().requireTrue(ErrorCode.CONFIG_LINK_PREVIEW_FUNCTION_REQUIRED)
    }

    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): LinkPreviewConfig =
            LinkPreviewConfig(
                functionName = env["LINK_PREVIEW_LAMBDA_FUNCTION"] ?: config.string("linkPreview.lambdaFunction"),
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
