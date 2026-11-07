package com.qlink.link.worker

import kotlinx.coroutines.channels.Channel

class LinkPreviewFillSignal {
    private val channel = Channel<Long>(Channel.UNLIMITED)

    fun wake(linkId: Long) {
        channel.trySend(linkId)
    }

    suspend fun await(): Long = channel.receive()
}
