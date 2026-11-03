package com.qlink.bookmarkimport.worker

import kotlinx.coroutines.channels.Channel

class ImportThumbnailSignal {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        channel.trySend(Unit)
    }

    suspend fun await() {
        channel.receive()
    }
}
