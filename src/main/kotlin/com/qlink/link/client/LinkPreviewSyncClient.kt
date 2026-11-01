package com.qlink.link.client

enum class LinkPreviewMode {
    PREVIEW,
    FAVICON_ONLY,
}

data class LinkPreviewResult(
    val title: String? = null,
    val thumbnailKey: String? = null,
    val faviconKey: String? = null,
    val isVideo: Boolean = false,
)

interface LinkPreviewSyncClient {
    suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        faviconBytes: ByteArray? = null,
    ): LinkPreviewResult?
}
