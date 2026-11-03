package com.qlink.link.client

interface LinkPreviewSyncClient {
    suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        caller: LinkPreviewCaller,
        faviconBytes: ByteArray? = null,
    ): LinkPreviewExtraction
}
