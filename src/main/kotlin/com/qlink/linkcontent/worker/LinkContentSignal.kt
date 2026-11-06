package com.qlink.linkcontent.worker

import kotlinx.coroutines.channels.Channel

class LinkContentSignal {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        channel.trySend(Unit)
    }

    suspend fun await() {
        channel.receive()
    }
}
