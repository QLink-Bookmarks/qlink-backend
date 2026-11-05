package com.qlink.linkcontent.client

import com.qlink.linkcontent.domain.ContentFailureCode

sealed interface LinkContentExtraction {
    data class Collected(
        val result: LinkContentResult,
    ) : LinkContentExtraction

    data class Failed(
        val code: ContentFailureCode,
        val upstreamStatus: Int? = null,
        val retryAfterSeconds: Int? = null,
    ) : LinkContentExtraction

    data class Blocked(
        val code: ContentFailureCode,
    ) : LinkContentExtraction

    data object Throttled : LinkContentExtraction

    data object Invalid : LinkContentExtraction
}
