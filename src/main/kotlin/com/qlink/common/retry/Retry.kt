package com.qlink.common.retry

import kotlinx.coroutines.delay

private const val DEFAULT_INITIAL_DELAY_MS = 100L

suspend fun <T> retryWithExponentialBackoff(
    maxAttempts: Int,
    initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    delayProvider: suspend (Long) -> Unit = { delay(it) },
    shouldRetry: (T) -> Boolean,
    block: suspend () -> T,
): T {
    val attempts = maxAttempts.coerceAtLeast(1)
    var nextDelayMs = initialDelayMs.coerceAtLeast(0)

    repeat(attempts - 1) {
        val result = block()
        if (!shouldRetry(result)) {
            return result
        }

        delayProvider(nextDelayMs)
        nextDelayMs *= 2
    }

    return block()
}
