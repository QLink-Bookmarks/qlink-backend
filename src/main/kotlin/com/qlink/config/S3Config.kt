package com.qlink.config

import io.ktor.server.config.ApplicationConfig

private const val DEFAULT_REGION = "ap-northeast-2"

data class S3Config(
    val region: String,
    val bucket: String,
    val endpoint: String?,
    val forcePathStyle: Boolean,
    val accessKeyId: String?,
    val secretAccessKey: String?,
    val publicBaseUrl: String?,
) {
    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): S3Config =
            S3Config(
                region = resolve(env, config, "AWS_S3_REGION", "aws.s3.region") ?: DEFAULT_REGION,
                bucket =
                    requireNotNull(resolve(env, config, "AWS_S3_BUCKET", "aws.s3.bucket")) {
                        "S3 bucket is not configured (AWS_S3_BUCKET or aws.s3.bucket)"
                    },
                endpoint = resolve(env, config, "AWS_S3_ENDPOINT", "aws.s3.endpoint"),
                forcePathStyle = resolve(env, config, "AWS_S3_FORCE_PATH_STYLE", "aws.s3.forcePathStyle").toBoolean(),
                accessKeyId = resolve(env, config, "AWS_S3_ACCESS_KEY_ID", "aws.s3.accessKeyId"),
                secretAccessKey = resolve(env, config, "AWS_S3_SECRET_ACCESS_KEY", "aws.s3.secretAccessKey"),
                publicBaseUrl = resolve(env, config, "AWS_S3_PUBLIC_BASE_URL", "aws.s3.publicBaseUrl"),
            )

        /**
         * Resolves a value with system env taking precedence over [application.yaml].
         *
         * When the env key is *present at all* it wins even if blank, letting production explicitly
         * disable a local default (e.g. `AWS_S3_ENDPOINT=""` to fall back to real AWS instead of LocalStack).
         * Blank resolves to null, so callers can apply their own default.
         */
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
