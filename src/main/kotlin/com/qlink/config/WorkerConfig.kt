package com.qlink.config

import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class WorkerConfig(
    val pollInterval: Duration,
    val importThumbnailConcurrency: Int,
    val importThumbnailParallelism: Int,
    val importThumbnailBatchSize: Int,
    val aiSummaryParallelism: Int,
) {
    init {
        require(pollInterval.isPositive())
        require(importThumbnailConcurrency > 0)
        require(importThumbnailParallelism > 0)
        require(importThumbnailBatchSize > 0)
        require(aiSummaryParallelism > 0)
    }

    companion object {
        fun from(config: ApplicationConfig): WorkerConfig =
            WorkerConfig(
                pollInterval = config.int("worker.pollIntervalMinutes").minutes,
                importThumbnailConcurrency = config.int("worker.importThumbnail.concurrency"),
                importThumbnailParallelism = config.int("worker.importThumbnail.parallelism"),
                importThumbnailBatchSize = config.int("worker.importThumbnail.batchSize"),
                aiSummaryParallelism = config.int("worker.aiSummary.parallelism"),
            )
    }
}
