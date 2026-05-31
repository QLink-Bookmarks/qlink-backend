package com.qlink.ai.worker

import kotlinx.coroutines.channels.Channel

class AiSummaryDispatcher(
    private val channel: Channel<Long>,
) {
    suspend fun dispatch(jobId: Long) {
        channel.send(jobId)
    }
}
