package com.qlink.linkcontent.worker

import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal class LinkContentHostGate(
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

    private companion object {
        const val MAX_HOSTS = 200
    }
}
