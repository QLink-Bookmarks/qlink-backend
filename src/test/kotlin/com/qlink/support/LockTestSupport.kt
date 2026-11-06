package com.qlink.support

import com.qlink.common.transaction.TransactionRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

suspend fun <T> runWhileLinkLocked(
    tx: TransactionRunner,
    holdLock: suspend () -> Unit,
    action: suspend () -> T,
): T =
    coroutineScope {
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val competitor =
            async {
                tx.required {
                    holdLock()
                    locked.complete(Unit)
                    release.await()
                }
            }
        locked.await()
        val result = async { action() }
        withTimeout(10.seconds) {
            while (tx.readOnly { countSessionsWaitingOnLinkLock() } == 0) {
                delay(20.milliseconds)
            }
        }
        release.complete(Unit)
        competitor.await()
        result.await()
    }

private fun countSessionsWaitingOnLinkLock(): Int =
    TransactionManager.current().exec(
        "SELECT count(*) FROM pg_stat_activity " +
            "WHERE wait_event_type = 'Lock' AND datname = current_database() " +
            "AND pid <> pg_backend_pid() AND query ILIKE '%links%'",
    ) { it.takeIf { row -> row.next() }?.getInt(1) } ?: 0
