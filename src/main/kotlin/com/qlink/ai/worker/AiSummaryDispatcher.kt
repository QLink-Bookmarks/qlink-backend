package com.qlink.ai.worker

import com.qlink.ai.client.AiProvider
import kotlinx.coroutines.channels.Channel

data class AiSummaryCommand(
    val ownerId: Long,
    val linkId: Long,
    val provider: AiProvider?,
)

class AiSummaryDispatcher(
    private val channel: Channel<AiSummaryCommand>,
) {
    suspend fun dispatch(command: AiSummaryCommand) {
        channel.send(command)
    }
}
