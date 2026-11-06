package com.qlink.support.fixture

import com.qlink.linkcontent.client.LinkContentExtraction
import com.qlink.linkcontent.client.LinkContentSyncClient

class FakeLinkContentSyncClient : LinkContentSyncClient {
    private val extractions = ArrayDeque<LinkContentExtraction>()
    var onInvoke: suspend () -> Unit = {}
    val calls = mutableListOf<String>()

    override suspend fun extract(
        url: String,
        traceId: String,
        deadlineMs: Long,
    ): LinkContentExtraction {
        calls += url
        onInvoke()
        return extractions.removeFirstOrNull() ?: LinkContentExtraction.Invalid
    }

    fun enqueue(vararg extraction: LinkContentExtraction) {
        extractions.addAll(extraction)
    }

    fun reset() {
        extractions.clear()
        onInvoke = {}
        calls.clear()
    }
}
