package com.qlink.config

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireTrue
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class LinkContentConfig(
    val functionName: String,
    val invokeTimeout: Duration,
    val workerEnabled: Boolean,
    val workerConcurrency: Int,
    val workerParallelism: Int,
    val workerBatchSize: Int,
    val workerMaxPages: Int,
    val hostMinInterval: Duration,
    val hostCooldown: Duration,
) {
    init {
        functionName.isNotBlank().requireTrue(ErrorCode.CONFIG_LINK_CONTENT_FUNCTION_REQUIRED)
        invokeTimeout.isPositive().requireTrue(ErrorCode.CONFIG_LINK_CONTENT_INVOKE_TIMEOUT_INVALID)
        (workerConcurrency > 0).requireTrue(ErrorCode.CONFIG_LINK_CONTENT_CONCURRENCY_INVALID)
        (workerParallelism > 0).requireTrue(ErrorCode.CONFIG_LINK_CONTENT_PARALLELISM_INVALID)
        (workerBatchSize > 0).requireTrue(ErrorCode.CONFIG_LINK_CONTENT_BATCH_SIZE_INVALID)
        (workerMaxPages > 0).requireTrue(ErrorCode.CONFIG_LINK_CONTENT_MAX_PAGES_INVALID)
        hostMinInterval.isNegative().requireFalse(ErrorCode.CONFIG_LINK_CONTENT_HOST_INTERVAL_INVALID)
        hostCooldown.isPositive().requireTrue(ErrorCode.CONFIG_LINK_CONTENT_HOST_COOLDOWN_INVALID)
    }

    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): LinkContentConfig =
            LinkContentConfig(
                functionName =
                    env["LINK_CONTENT_LAMBDA_FUNCTION"]?.takeIf(String::isNotBlank) ?: config.string("linkContent.lambdaFunction"),
                invokeTimeout = config.int("linkContent.invokeTimeoutSeconds").seconds,
                workerEnabled = config.boolean("worker.linkContent.enabled"),
                workerConcurrency = config.int("worker.linkContent.concurrency"),
                workerParallelism = config.int("worker.linkContent.parallelism"),
                workerBatchSize = config.int("worker.linkContent.batchSize"),
                workerMaxPages = config.int("worker.linkContent.maxPages"),
                hostMinInterval = config.int("worker.linkContent.hostMinIntervalMillis").milliseconds,
                hostCooldown = config.int("worker.linkContent.hostCooldownSeconds").seconds,
            )
    }
}
