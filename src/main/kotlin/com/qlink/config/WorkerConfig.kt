package com.qlink.config

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
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
        pollInterval.isPositive().requireTrue(ErrorCode.CONFIG_WORKER_POLL_INTERVAL_INVALID)
        (importThumbnailConcurrency > 0).requireTrue(ErrorCode.CONFIG_IMPORT_THUMBNAIL_CONCURRENCY_INVALID)
        (importThumbnailParallelism > 0).requireTrue(ErrorCode.CONFIG_IMPORT_THUMBNAIL_PARALLELISM_INVALID)
        (importThumbnailBatchSize > 0).requireTrue(ErrorCode.CONFIG_IMPORT_THUMBNAIL_BATCH_SIZE_INVALID)
        (aiSummaryParallelism > 0).requireTrue(ErrorCode.CONFIG_AI_SUMMARY_PARALLELISM_INVALID)
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
