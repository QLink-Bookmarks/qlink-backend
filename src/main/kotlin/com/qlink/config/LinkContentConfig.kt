package com.qlink.config

import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 링크 본문 수집 설정. Lambda 클라이언트는 링크 프리뷰와 같은 `LambdaClient`(리전·LocalStack 엔드포인트 규칙)를 쓴다.
 * 타임아웃 사슬: Lambda 핸들러 deadline 20초(서비스) < Lambda 타임아웃 30초(Terraform) < [invokeTimeout] 45초.
 */
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
        require(functionName.isNotBlank())
        require(invokeTimeout.isPositive())
        require(workerConcurrency > 0)
        require(workerParallelism > 0)
        require(workerBatchSize > 0)
        require(workerMaxPages > 0)
        require(!hostMinInterval.isNegative())
        require(hostCooldown.isPositive())
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
