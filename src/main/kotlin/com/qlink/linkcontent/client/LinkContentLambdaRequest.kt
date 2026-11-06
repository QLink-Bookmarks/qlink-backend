package com.qlink.linkcontent.client

import kotlinx.serialization.Serializable

@Serializable
internal data class LinkContentLambdaRequest(
    val url: String,
    val traceId: String,
    val deadlineMs: Long,
)
