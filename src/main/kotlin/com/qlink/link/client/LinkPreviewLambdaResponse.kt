package com.qlink.link.client

import kotlinx.serialization.Serializable

@Serializable
internal data class LinkPreviewLambdaResponse(
    val title: String? = null,
    val thumbnailKey: String? = null,
    val faviconKey: String? = null,
    val isVideo: Boolean = false,
) {
    fun toResult(): LinkPreviewResult =
        LinkPreviewResult(
            title = title,
            thumbnailKey = thumbnailKey,
            faviconKey = faviconKey,
            isVideo = isVideo,
        )
}
