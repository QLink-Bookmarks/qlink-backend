package com.qlink.support

import com.qlink.link.worker.LinkPreviewFillSignal
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

suspend fun LinkPreviewFillSignal.drainLinkIds(): List<Long> =
    buildList {
        while (true) {
            add(withTimeoutOrNull(100.milliseconds) { await() } ?: break)
        }
    }
