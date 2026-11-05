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

/**
 * 링크 본문 수집 워커(서버 프로세스 안). 서비스만 호출한다(D3).
 *
 * - scanner: 시그널을 받으면 [drain]한다. 한 번의 drain은 sweep을 최대 [LinkContentConfig.workerMaxPages]페이지 진행한다.
 * - sweep: 시작 시각에 고정한 due 집합을 keyset `(priority, next_attempt_at, id)` 순으로 EOF까지 읽는 단위. EOF 전에는 cursor를 버리지 않는다.
 * - timer: sweep이 끝나면 다음 실행 가능 시각·쿨다운 만료에 한 번 깨운다(취소·재예약 가능한 코루틴 하나).
 * - 일시 정지: throttle·오류로 배치가 멈추면 [INTERRUPT_DELAY] 동안 어떤 wake에도 drain하지 않는다(클레임 소진 방지).
 * - poller: [pollInterval]마다 깨운다.
 * - 호스트: 같은 호스트 요청 간 [LinkContentConfig.hostMinInterval] + jitter, 429 호스트는 인스턴스 메모리 쿨다운.
 */
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

    // scanner 코루틴만 읽고 쓴다.
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
            // 중단된 페이지는 cursor를 옮기지 않는다. 다시 읽으면 처리한 행은 더 이상 due가 아니어서 남은 행만 나온다.
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
            // sweep 시작 뒤 due가 된 행이 있으면 바로 새 sweep을 연다. 쿨다운 호스트 행만 남았으면 이 sweep이 이미 건너뛴 것이다.
            schedule.due > 0 && (finished.processed > 0 || finished.skipped == 0L) -> signal.wake()

            // 아무것도 처리하지 못한 sweep(전부 쿨다운)은 가장 이른 쿨다운 만료보다 이르게 깨우지 않는다.
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

    /** 결과 outcome. 서비스 예외면 null(배치 중단; 행은 stale 회수로 다시 클레임된다). */
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

    /** 예외 클래스와 SQLState만 남긴다. 메시지에는 PgJDBC "Failing row contains (...)"처럼 본문·URL이 들어갈 수 있다. */
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

    /** 호스트별 요청 간격과 429 쿨다운(인스턴스 메모리, 각 [MAX_HOSTS]개 상한). */
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

        /** 가득 차면 만료가 가장 이른 항목을 교체한다. */
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

        /** 이 호스트의 다음 요청 시각을 예약하고 그때까지 기다릴 시간을 돌려준다. 가득 차면 가장 오래된 항목을 뺀다. */
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
