package com.qlink.link.worker

import com.qlink.common.logging.traceContext
import com.qlink.link.service.FillLinkPreviewService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger

class LinkPreviewFillWorker(
    private val signal: LinkPreviewFillSignal,
    private val fillService: FillLinkPreviewService,
    private val traceIdLength: Int,
    private val log: Logger,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1, DISPATCHER_NAME)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start() {
        log.info("[LINK_PREVIEW_FILL] Starting link preview fill worker")
        scope.launch(CoroutineName("$DISPATCHER_NAME-consumer")) {
            while (isActive) {
                val linkId = signal.await()
                withContext(traceContext(traceIdLength)) {
                    try {
                        fillService.fill(linkId)
                    } catch (error: Throwable) {
                        if (error is CancellationException) currentCoroutineContext().ensureActive()
                        log.warn("[LINK_PREVIEW_FILL] Failed to fill link preview. linkId={}", linkId, error)
                    }
                }
            }
        }
    }

    fun stop() {
        log.info("[LINK_PREVIEW_FILL] Stopping link preview fill worker")
        scope.cancel()
    }

    private companion object {
        const val DISPATCHER_NAME = "link-preview-fill-worker"
    }
}
