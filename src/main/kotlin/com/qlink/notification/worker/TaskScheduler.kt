package com.qlink.notification.worker

import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.todo.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

private val DAILY_BATCH_TIME = LocalTime.of(23, 55)
private val UTC = ZoneOffset.UTC

class TaskScheduler(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val todoRepository: TodoRepository,
    private val log: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationJobs = ConcurrentHashMap<Long, Job>()

    fun start() {
        log.info("[TASK_SCHEDULER] Starting task scheduler")
        scope.launch {
            schedulePendingForUtcDate(utcToday())
        }
        scope.launch {
            startDailyBatch()
        }
    }

    fun stop() {
        log.info("[TASK_SCHEDULER] Stopping task scheduler")
        scope.cancel()
        notificationJobs.clear()
    }

    fun scheduleIfToday(notification: Notification) {
        if (notification.isPending && notification.willFireAt.isInUtcDate(utcToday())) {
            schedule(notification.id!!)
        }
    }

    fun schedule(notificationId: Long) {
        cancel(notificationId)

        val job =
            scope.launch {
                val notification =
                    tx.required {
                        val pending = notificationRepository.findPendingById(notificationId) ?: return@required null
                        notificationRepository.update(pending.markScheduled(Clock.System.now()))
                    } ?: return@launch

                delay(notification.delayFromNow())
                fire(notification.id!!)
            }

        notificationJobs[notificationId] = job
    }

    fun cancel(notificationId: Long) {
        notificationJobs.remove(notificationId)?.cancel()
    }

    private suspend fun startDailyBatch() {
        while (scope.isActive) {
            delayUntilNextDailyBatch()
            schedulePendingForUtcDate(utcToday().plusDays(1))
        }
    }

    private suspend fun schedulePendingForUtcDate(date: LocalDate) {
        val bounds = date.utcBounds()
        val notifications =
            tx.required {
                createMissingTodoNotifications(
                    startInclusive = bounds.first,
                    endExclusive = bounds.second,
                )
                notificationRepository.findPendingBetween(
                    startInclusive = bounds.first,
                    endExclusive = bounds.second,
                )
            }

        notifications.forEach { schedule(it.id!!) }
    }

    private suspend fun createMissingTodoNotifications(
        startInclusive: Instant,
        endExclusive: Instant,
    ) {
        todoRepository
            .findAllWithReminderBetween(
                startInclusive = startInclusive,
                endExclusive = endExclusive,
            ).forEach { todo ->
                val todoId = todo.id ?: return@forEach
                val exists =
                    notificationRepository
                        .findPendingByContext(
                            context = NotificationContext.TODO,
                            contextId = todoId,
                        ).any { it.willFireAt == todo.reminderAt }

                if (!exists) {
                    Notification.todo(todo)?.let { notificationRepository.insert(it) }
                }
            }
    }

    private suspend fun fire(notificationId: Long) {
        runCatching {
            tx.required {
                val notification = notificationRepository.findPendingById(notificationId) ?: return@required
                notificationRepository.update(notification.markFired(Clock.System.now()))

                if (notification.context == NotificationContext.TODO) {
                    val todo = todoRepository.findById(notification.contextId) ?: return@required
                    if (!todo.hasRepeat) {
                        return@required
                    }

                    val nextTodo = todo.setNextReminder(Clock.System.now())
                    val savedTodo = todoRepository.update(nextTodo)

                    Notification.todo(savedTodo)?.let { nextNotification ->
                        val exists =
                            notificationRepository
                                .findPendingByContext(
                                    context = NotificationContext.TODO,
                                    contextId = savedTodo.id!!,
                                ).any { it.willFireAt == nextNotification.willFireAt }

                        if (!exists) {
                            notificationRepository.insert(nextNotification)
                        }
                    }
                }
            }
        }.onFailure { exception ->
            log.warn("[TASK_SCHEDULER] Failed to fire notification. notificationId=$notificationId", exception)
            markFailed(notificationId)
        }

        notificationJobs.remove(notificationId)
    }

    private suspend fun markFailed(notificationId: Long) {
        tx.required {
            val notification = notificationRepository.findPendingById(notificationId) ?: return@required
            notificationRepository.update(notification.markFailed(Clock.System.now()))
        }
    }

    private suspend fun delayUntilNextDailyBatch() {
        delay((nextDailyBatchAt() - Clock.System.now()).coerceAtLeast(ZERO))
    }

    private fun nextDailyBatchAt(now: Instant = Clock.System.now()): Instant {
        val zonedNow = now.toJavaInstant().atZone(UTC)
        val todayBatch =
            zonedNow
                .toLocalDate()
                .atTime(DAILY_BATCH_TIME)
                .atZone(UTC)
                .toInstant()
                .toKotlinInstant()

        return if (todayBatch > now) {
            todayBatch
        } else {
            todayBatch.plus(1.days)
        }
    }

    private fun Notification.delayFromNow(): Duration = (willFireAt - Clock.System.now()).coerceAtLeast(ZERO)

    private fun utcToday(): LocalDate =
        Clock.System
            .now()
            .toJavaInstant()
            .atZone(UTC)
            .toLocalDate()

    private fun Instant.isInUtcDate(date: LocalDate): Boolean {
        val bounds = date.utcBounds()
        return this >= bounds.first && this < bounds.second
    }

    private fun LocalDate.utcBounds(): Pair<Instant, Instant> {
        val start = atStartOfDay(UTC).toInstant().toKotlinInstant()
        return start to plusDays(1).atStartOfDay(UTC).toInstant().toKotlinInstant()
    }
}
