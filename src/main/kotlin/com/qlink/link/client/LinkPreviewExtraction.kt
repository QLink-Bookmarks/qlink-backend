package com.qlink.link.client

sealed interface LinkPreviewExtraction {
    data class Success(
        val result: LinkPreviewResult,
    ) : LinkPreviewExtraction

    data object Failed : LinkPreviewExtraction

    data object Throttled : LinkPreviewExtraction
}
