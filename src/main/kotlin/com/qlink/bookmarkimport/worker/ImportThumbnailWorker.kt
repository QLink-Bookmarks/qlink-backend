package com.qlink.bookmarkimport.worker

import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.ImportThumbnailTargetQuery
import com.qlink.bookmarkimport.service.CountImportThumbnailTargetsService
import com.qlink.bookmarkimport.service.FillImportThumbnailService
import com.qlink.bookmarkimport.service.GetImportThumbnailTargetsService
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requirePresent
import com.qlink.common.text.hostOrNull
import com.qlink.config.WorkerConfig
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ImportThumbnailWorker(
    private val signal: ImportThumbnailSignal,
    private val getTargetsService: GetImportThumbnailTargetsService,
    private val countTargetsService: CountImportThumbnailTargetsService,
    private val fillService: FillImportThumbnailService,
    private val config: WorkerConfig,
    private val meterRegistry: MeterRegistry,
    private val log: Logger,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(config.importThumbnailParallelism, DISPATCHER_NAME)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val inFlight =
        meterRegistry
            .gauge(
                IN_FLIGHT_METRIC,
                AtomicInteger(0),
            ).requirePresent(ErrorCode.COMMON_METRIC_REGISTRATION_FAILED)
    private val pending = meterRegistry.gauge(PENDING_METRIC, AtomicLong(0)).requirePresent(ErrorCode.COMMON_METRIC_REGISTRATION_FAILED)

    fun start() {
        scope.launch(CoroutineName("$DISPATCHER_NAME-poller")) {
            while (isActive) {
                signal.wake()
                delay(config.pollInterval)
            }
        }
        scope.launch(CoroutineName("$DISPATCHER_NAME-scanner")) {
            while (isActive) {
                signal.await()
                try {
                    drain()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.warn("[IMPORT_THUMBNAIL] Failed to drain thumbnail targets", error)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun drain() {
        do {
            refreshPending()
            val targets = getTargetsService.getTargets(config.importThumbnailBatchSize)
            val isSettled = targets.isNotEmpty() && process(targets)
        } while (isSettled)
        refreshPending()
    }

    private suspend fun process(targets: List<ImportThumbnailTargetQuery>): Boolean {
        val sites = Channel<List<ImportThumbnailTargetQuery>>(Channel.UNLIMITED)
        targets.groupBy { it.url.hostOrNull() ?: it.url }.values.forEach { sites.trySend(it) }
        sites.close()
        val isSettled = AtomicBoolean(true)

        coroutineScope {
            repeat(config.importThumbnailConcurrency) { index ->
                launch(CoroutineName("$DISPATCHER_NAME-$index")) {
                    for (site in sites) {
                        site.forEach { target -> if (!fill(target)) isSettled.set(false) }
                    }
                }
            }
        }
        return isSettled.get()
    }

    private suspend fun fill(target: ImportThumbnailTargetQuery): Boolean {
        inFlight.incrementAndGet()
        val result =
            try {
                resultOf(fillService.fill(target.importLinkId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.warn("[IMPORT_THUMBNAIL] Failed to fill thumbnail. importLinkId={}", target.importLinkId, error)
                RESULT_ERROR
            } finally {
                inFlight.decrementAndGet()
            }

        Counter
            .builder(RESULT_METRIC)
            .tag("result", result)
            .register(meterRegistry)
            .increment()
        return result != RESULT_THROTTLED && result != RESULT_ERROR
    }

    private suspend fun refreshPending() {
        pending.set(countTargetsService.count())
    }

    private fun resultOf(status: ThumbnailStatus?): String =
        when (status) {
            null -> RESULT_SKIPPED
            ThumbnailStatus.PENDING -> RESULT_THROTTLED
            ThumbnailStatus.DONE -> "done"
            ThumbnailStatus.FAILED -> "failed"
            ThumbnailStatus.STOPPED -> "stopped"
        }

    private companion object {
        const val DISPATCHER_NAME = "import-thumbnail-worker"
        const val IN_FLIGHT_METRIC = "import_thumbnail_in_flight"
        const val PENDING_METRIC = "import_thumbnail_pending"
        const val RESULT_METRIC = "import_thumbnail_results"
        const val RESULT_SKIPPED = "skipped"
        const val RESULT_THROTTLED = "throttled"
        const val RESULT_ERROR = "error"
    }
}
