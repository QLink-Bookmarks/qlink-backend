package com.qlink.linkcontent.worker

import com.qlink.config.LinkContentConfig
import com.qlink.linkcontent.domain.CollectOutcome
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.slf4j.Logger
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    private val log: Logger,
    private val clock: Clock = Clock.System,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(config.workerParallelism, DISPATCHER_NAME)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val hosts = HostGate(minInterval = config.hostMinInterval, clock = clock)
    private val inFlight = AtomicInteger(0)
    private val due = AtomicLong(0)
    private val pending = AtomicLong(0)
    private val oldestDueAge = AtomicLong(0)

    private var sweep: Sweep? = null
    private var timer: Job? = null
    private var pausedUntil: Instant? = null

    fun start() {
        if (!config.workerEnabled) {
            log.info("[LINK_CONTENT] worker disabled")
            return
        }
        meterRegistry.gauge(IN_FLIGHT_METRIC, inFlight)
        meterRegistry.gauge(DUE_METRIC, due)
        meterRegistry.gauge(PENDING_METRIC, pending)
        meterRegistry.gauge(OLDEST_DUE_AGE_METRIC, oldestDueAge)

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
                try {
                    drain()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.warn("[LINK_CONTENT] Failed to drain link content targets. cause={}", error.describe())
                    pause()
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun drain() {
        val current = sweep ?: Sweep(startedAt = clock.now()).also { sweep = it }
        var pages = 0
        var isInterrupted = false
        var isEnd = false

        while (pages < config.workerMaxPages && !isInterrupted && !isEnd) {
            val targets =
                getTargetsService.getTargets(
                    size = config.workerBatchSize,
                    dueBefore = current.startedAt,
                    after = current.cursor,
                    now = clock.now(),
                )
            pages++
            val page = process(targets)
            current.processed += page.processed
            current.skipped += page.skipped
            isInterrupted = page.isInterrupted
            isEnd = targets.size < config.workerBatchSize
            if (!isInterrupted) targets.lastOrNull()?.let { current.cursor = it.keyset }
        }

        when {
            isInterrupted -> {
                refreshGauges()
                pause()
            }

            isEnd -> {
                finishSweep(current)
            }

            else -> {
                yield()
                signal.wake()
            }
        }
    }

    private suspend fun finishSweep(finished: Sweep) {
        sweep = null
        val schedule = refreshGauges()
        val cooldownEndsAt = hosts.earliestCooldownEnd(clock.now())

        when {
            schedule.due > 0 && (finished.processed > 0 || finished.skipped == 0L) -> signal.wake()
            finished.processed == 0L && cooldownEndsAt != null -> scheduleWake(cooldownEndsAt)
            else -> listOfNotNull(schedule.nextRunnableAt, cooldownEndsAt).minOrNull()?.let(::scheduleWake)
        }
    }

    private suspend fun process(targets: List<LinkContentTargetQuery>): Page {
        val now = clock.now()
        val (cooled, open) = targets.partition { hosts.isCooling(host = it.host, now = now) }
        val processed = AtomicLong(0)
        val skipped = AtomicLong(cooled.size.toLong())
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
                            processed.incrementAndGet()
                            if (outcome == null || outcome in INTERRUPTING_OUTCOMES) isInterrupted.set(true)
                        }
                    }
                }
            }
        }
        return Page(processed = processed.get(), skipped = skipped.get(), isInterrupted = isInterrupted.get())
    }

    private suspend fun collect(target: LinkContentTargetQuery): CollectOutcome? {
        val started = TimeSource.Monotonic.markNow()
        inFlight.incrementAndGet()
        val result =
            try {
                collectService.collect(contentId = target.contentId, now = clock.now())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
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
        val outcome = result?.outcome?.name ?: OUTCOME_ERROR
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
        oldestDueAge.set(schedule.oldestDueAgeSeconds ?: 0)
        return schedule
    }

    private fun pause() {
        val resumeAt = clock.now() + INTERRUPT_DELAY
        pausedUntil = resumeAt
        scheduleWake(resumeAt)
    }

    private fun Exception.describe(): String {
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

    private class Sweep(
        val startedAt: Instant,
    ) {
        var cursor: DueKeyset? = null
        var processed: Long = 0
        var skipped: Long = 0
    }

    private data class Page(
        val processed: Long,
        val skipped: Long,
        val isInterrupted: Boolean,
    )

    private class HostGate(
        private val minInterval: Duration,
        private val clock: Clock,
    ) {
        private val cooldowns = HashMap<String, Instant>()
        private val nextAllowed = LinkedHashMap<String, Instant>()

        @Synchronized
        fun isCooling(
            host: String,
            now: Instant,
        ): Boolean {
            val until = cooldowns[host] ?: return false
            if (until > now) return true
            cooldowns.remove(host)
            return false
        }

        @Synchronized
        fun cool(
            host: String,
            until: Instant,
        ) {
            if (host !in cooldowns && cooldowns.size >= MAX_HOSTS) {
                cooldowns.minByOrNull { it.value }?.let { cooldowns.remove(it.key) }
            }
            cooldowns[host] = maxOf(until, cooldowns[host] ?: until)
        }

        @Synchronized
        fun earliestCooldownEnd(now: Instant): Instant? {
            cooldowns.entries.removeIf { it.value <= now }
            return cooldowns.values.minOrNull()
        }

        suspend fun awaitTurn(host: String) {
            val wait = reserve(host)
            if (wait.isPositive()) delay(wait)
        }

        @Synchronized
        private fun reserve(host: String): Duration {
            val now = clock.now()
            val at = maxOf(now, nextAllowed.remove(host) ?: now)
            if (nextAllowed.size >= MAX_HOSTS) nextAllowed.remove(nextAllowed.keys.first())
            nextAllowed[host] = at + minInterval + jitter()
            return at - now
        }

        private fun jitter(): Duration = Random.nextLong(0, minInterval.inWholeMilliseconds / 2 + 1).milliseconds
    }

    private companion object {
        const val DISPATCHER_NAME = "link-content-worker"
        const val IN_FLIGHT_METRIC = "link_content_in_flight"
        const val DUE_METRIC = "link_content_due"
        const val PENDING_METRIC = "link_content_pending"
        const val OLDEST_DUE_AGE_METRIC = "link_content_oldest_due_age_seconds"
        const val RESULT_METRIC = "link_content_results"
        const val OUTCOME_ERROR = "ERROR"
        const val MAX_HOSTS = 200
        val MIN_TIMER_DELAY = 1.seconds
        val INTERRUPT_DELAY = 1.minutes
        val INTERRUPTING_OUTCOMES = setOf(CollectOutcome.RELEASED_THROTTLED, CollectOutcome.RELEASED_ERROR)
    }
}
