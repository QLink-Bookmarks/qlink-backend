package com.qlink.link.client

import kotlinx.serialization.Serializable

@Serializable
internal data class LinkPreviewLambdaRequest(
    val url: String,
    val mode: String,
    val traceId: String,
    val favicon: LinkPreviewLambdaFavicon? = null,
)
