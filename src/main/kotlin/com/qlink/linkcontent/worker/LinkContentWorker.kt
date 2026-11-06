package com.qlink.linkcontent.worker

import com.qlink.common.logging.traceContext
import com.qlink.config.LinkContentConfig
import com.qlink.linkcontent.domain.CollectOutcome
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.dto.DueKeyset
import com.qlink.linkcontent.dto.LinkContentScheduleQuery
import com.qlink.linkcontent.dto.LinkContentTargetQuery
import com.qlink.linkcontent.service.CollectLinkContentService
import com.qlink.linkcontent.service.GetLinkContentScheduleService
import com.qlink.linkcontent.service.GetLinkContentTargetsService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.slf4j.Logger
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

class LinkContentWorker(
    private val signal: LinkContentSignal,
    private val getTargetsService: GetLinkContentTargetsService,
    private val getScheduleService: GetLinkContentScheduleService,
    private val collectService: CollectLinkContentService,
    private val config: LinkContentConfig,
    private val pollInterval: Duration,
    private val meterRegistry: MeterRegistry,
    private val traceIdLength: Int,
    private val clock: Clock = Clock.System,
    private val log: Logger,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(config.workerParallelism, DISPATCHER_NAME)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val hosts = LinkContentHostGate(minInterval = config.hostMinInterval, clock = clock)
    private val inFlight = AtomicInteger(0)
    private val due = AtomicLong(0)
    private val pending = AtomicLong(0)
    private val oldestDueAgeSeconds = AtomicLong(0)

    private var sweepStartedAt: Instant? = null
    private var sweepCursor: DueKeyset? = null
    private var sweepProcessed: Long = 0
    private var sweepSkipped: Long = 0
    private val sweepErroredIds = mutableSetOf<Long>()
    private var timer: Job? = null
    private var pausedUntil: Instant? = null

    fun start() {
        if (!config.workerEnabled) {
            log.info("[LINK_CONTENT] worker disabled")
            return
        }
        log.info("[LINK_CONTENT] Starting link content worker")
        meterRegistry.gauge(IN_FLIGHT_METRIC, inFlight)
        meterRegistry.gauge(DUE_METRIC, due)
        meterRegistry.gauge(PENDING_METRIC, pending)
        meterRegistry.gauge(OLDEST_DUE_AGE_METRIC, oldestDueAgeSeconds)

        scope.launch(CoroutineName("$DISPATCHER_NAME-poller")) {
            while (isActive) {
                signal.wake()
                delay(pollInterval)
            }
        }
        scope.launch(CoroutineName("$DISPATCHER_NAME-scanner")) {
            while (isActive) {
                signal.await()
                val resumeAt = pausedUntil
                if (resumeAt != null && clock.now() < resumeAt) {
                    scheduleWake(resumeAt)
                    continue
                }
                pausedUntil = null
                withContext(traceContext(traceIdLength)) {
                    try {
                        drain()
                    } catch (error: Throwable) {
                        if (error is CancellationException) currentCoroutineContext().ensureActive()
                        log.warn("[LINK_CONTENT] Failed to drain link content targets. cause={}", error.describe())
                        pause()
                    }
                }
            }
        }
    }

    fun stop() {
        if (config.workerEnabled) log.info("[LINK_CONTENT] Stopping link content worker")
        scope.cancel()
    }

    private suspend fun drain() {
        val startedAt = sweepStartedAt ?: clock.now().also { sweepStartedAt = it }
        var pages = 0
        var isInterrupted = false
        var isEnd = false

        while (pages < config.workerMaxPages && !isInterrupted && !isEnd) {
            if (sweepCursor?.priority == ContentPriority.BULK) {
                isInterrupted = process(dueInteractiveTargets())
            }
            if (!isInterrupted) {
                val targets =
                    getTargetsService.getTargets(
                        size = config.workerBatchSize,
                        dueBefore = startedAt,
                        after = sweepCursor,
                        now = clock.now(),
                    )
                pages++
                isInterrupted = process(targets)
                isEnd = targets.size < config.workerBatchSize
                if (!isInterrupted) targets.lastOrNull()?.let { sweepCursor = it.keyset }
            }
        }

        when {
            isInterrupted -> {
                refreshGauges()
                pause()
            }

            isEnd -> {
                finishSweep()
            }

            else -> {
                yield()
                signal.wake()
            }
        }
    }

    private suspend fun dueInteractiveTargets(): List<LinkContentTargetQuery> {
        val now = clock.now()
        return getTargetsService
            .getTargets(size = config.workerBatchSize, dueBefore = now, after = null, now = now)
            .filter { it.keyset.priority == ContentPriority.INTERACTIVE && it.contentId !in sweepErroredIds }
    }

    private suspend fun finishSweep() {
        val processed = sweepProcessed
        val skipped = sweepSkipped
        val hasErrors = sweepErroredIds.isNotEmpty()
        sweepStartedAt = null
        sweepCursor = null
        sweepProcessed = 0
        sweepSkipped = 0
        sweepErroredIds.clear()
        val schedule = refreshGauges()
        val cooldownEndsAt = hosts.earliestCooldownEnd(clock.now())

        when {
            schedule.due > 0 && !hasErrors && (processed > 0 || skipped == 0L) -> signal.wake()
            processed == 0L && cooldownEndsAt != null -> scheduleWake(cooldownEndsAt)
            else -> listOfNotNull(schedule.nextRunnableAt, cooldownEndsAt).minOrNull()?.let(::scheduleWake)
        }
    }

    private suspend fun process(targets: List<LinkContentTargetQuery>): Boolean {
        val now = clock.now()
        val (cooled, open) = targets.partition { hosts.isCooling(host = it.host, now = now) }
        val processed = AtomicLong(0)
        val skipped = AtomicLong(cooled.size.toLong())
        val errored = ConcurrentHashMap.newKeySet<Long>()
        val isInterrupted = AtomicBoolean(false)
        val groups = Channel<List<LinkContentTargetQuery>>(Channel.UNLIMITED)
        open.groupBy { it.host }.values.forEach { groups.trySend(it) }
        groups.close()

        coroutineScope {
            repeat(config.workerConcurrency) { index ->
                launch(CoroutineName("$DISPATCHER_NAME-$index")) {
                    for (group in groups) {
                        for (target in group) {
                            if (isInterrupted.get()) break
                            if (hosts.isCooling(host = target.host, now = clock.now())) {
                                skipped.incrementAndGet()
                                continue
                            }
                            hosts.awaitTurn(target.host)
                            val outcome = collect(target)
                            if (outcome == null) errored += target.contentId else processed.incrementAndGet()
                            if (outcome in INTERRUPTING_OUTCOMES) isInterrupted.set(true)
                        }
                    }
                }
            }
        }
        sweepProcessed += processed.get()
        sweepSkipped += skipped.get()
        sweepErroredIds += errored
        return isInterrupted.get()
    }

    private suspend fun collect(target: LinkContentTargetQuery): CollectOutcome? {
        val started = TimeSource.Monotonic.markNow()
        inFlight.incrementAndGet()
        val result =
            try {
                collectService.collect(contentId = target.contentId, now = clock.now())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.warn(
                    "[LINK_CONTENT] Failed to collect. contentId={} linkId={} cause={}",
                    target.contentId,
                    target.linkId,
                    error.describe(),
                )
                null
            } finally {
                inFlight.decrementAndGet()
            }

        result?.host?.let { host ->
            val cooldown = result.cooldownSeconds?.takeIf { it > 0 }?.seconds ?: config.hostCooldown
            hosts.cool(host = host, until = clock.now() + cooldown)
        }
        val outcome = result?.outcome?.name?.lowercase() ?: OUTCOME_ERROR
        Counter
            .builder(RESULT_METRIC)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
        log.info(
            "[LINK_CONTENT] contentId={} linkId={} outcome={} status={} durationMs={}",
            target.contentId,
            target.linkId,
            outcome,
            result?.status,
            started.elapsedNow().inWholeMilliseconds,
        )
        return result?.outcome
    }

    private suspend fun refreshGauges(): LinkContentScheduleQuery {
        val schedule = getScheduleService.getSchedule(clock.now())
        due.set(schedule.due)
        pending.set(schedule.pending)
        oldestDueAgeSeconds.set(schedule.oldestDueAge?.inWholeSeconds ?: 0)
        return schedule
    }

    private fun pause() {
        val resumeAt = clock.now() + INTERRUPT_DELAY
        pausedUntil = resumeAt
        scheduleWake(resumeAt)
    }

    private fun Throwable.describe(): String {
        val sqlState =
            generateSequence<Throwable>(this) { it.cause }
                .filterIsInstance<SQLException>()
                .firstNotNullOfOrNull { it.sqlState }
        return listOfNotNull(this::class.simpleName, sqlState?.let { "sqlState=$it" }).joinToString(" ")
    }

    private fun scheduleWake(at: Instant) {
        timer?.cancel()
        val wait = (at - clock.now()).coerceAtLeast(MIN_TIMER_DELAY)
        timer =
            scope.launch(CoroutineName("$DISPATCHER_NAME-timer")) {
                delay(wait)
                signal.wake()
            }
    }

    private companion object {
        const val DISPATCHER_NAME = "link-content-worker"
        const val IN_FLIGHT_METRIC = "link_content_in_flight"
        const val DUE_METRIC = "link_content_due"
        const val PENDING_METRIC = "link_content_pending"
        const val OLDEST_DUE_AGE_METRIC = "link_content_oldest_due_age_seconds"
        const val RESULT_METRIC = "link_content_results"
        const val OUTCOME_ERROR = "error"
        val MIN_TIMER_DELAY = 1.seconds
        val INTERRUPT_DELAY = 1.minutes
        val INTERRUPTING_OUTCOMES = setOf(CollectOutcome.RELEASED_THROTTLED, CollectOutcome.RELEASED_ERROR)
    }
}
