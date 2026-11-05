package com.qlink.config

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requirePresent
import com.qlink.common.error.requireTrue
import io.ktor.server.config.ApplicationConfig

private const val DEFAULT_REGION = "ap-northeast-2"

data class S3Config(
    val region: String,
    val profileBucket: String,
    val postBucket: String,
    val previewBucket: String,
    val endpoint: String?,
    val forcePathStyle: Boolean,
    val publicBaseUrl: String,
    val environment: String,
) {
    init {
        (environment == "dev" || environment == "prod").requireTrue(ErrorCode.CONFIG_S3_ENVIRONMENT_INVALID)
        (publicBaseUrl.startsWith("https://") || publicBaseUrl.startsWith("http://localhost"))
            .requireTrue(ErrorCode.CONFIG_S3_PUBLIC_BASE_URL_INVALID)
    }

    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): S3Config =
            S3Config(
                region = resolve(env, config, "AWS_S3_REGION", "aws.s3.region") ?: DEFAULT_REGION,
                profileBucket = required(env, config, "AWS_S3_PROFILE_BUCKET", "aws.s3.profileBucket"),
                postBucket = required(env, config, "AWS_S3_POST_BUCKET", "aws.s3.postBucket"),
                previewBucket = required(env, config, "AWS_S3_PREVIEW_BUCKET", "aws.s3.previewBucket"),
                endpoint = resolve(env, config, "AWS_S3_ENDPOINT", "aws.s3.endpoint"),
                forcePathStyle = resolve(env, config, "AWS_S3_FORCE_PATH_STYLE", "aws.s3.forcePathStyle").toBoolean(),
                publicBaseUrl = required(env, config, "AWS_S3_PUBLIC_BASE_URL", "aws.s3.publicBaseUrl"),
                environment = (env["APP_ENV"] ?: "dev").lowercase(),
            )

        private fun required(
            env: Map<String, String>,
            config: ApplicationConfig,
            envKey: String,
            yamlPath: String,
        ): String = resolve(env, config, envKey, yamlPath).requirePresent(ErrorCode.CONFIG_S3_SETTING_REQUIRED)

        private fun resolve(
            env: Map<String, String>,
            config: ApplicationConfig,
            envKey: String,
            yamlPath: String,
        ): String? =
            (if (env.containsKey(envKey)) env[envKey] else config.optionalString(yamlPath))
                ?.takeIf(String::isNotBlank)
    }
}
