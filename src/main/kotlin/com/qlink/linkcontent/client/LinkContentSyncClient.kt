package com.qlink.linkcontent.client

interface LinkContentSyncClient {
    suspend fun extract(
        url: String,
        traceId: String,
        deadlineMs: Long,
    ): LinkContentExtraction
}
