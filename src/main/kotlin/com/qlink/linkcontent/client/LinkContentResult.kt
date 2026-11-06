package com.qlink.linkcontent.client

import kotlin.time.Instant

data class LinkContentResult(
    val text: String,
    val title: String? = null,
    val siteName: String? = null,
    val author: String? = null,
    val publishedAt: Instant? = null,
    val canonicalUrl: String? = null,
    val finalUrl: String? = null,
    val language: String? = null,
    val isTruncated: Boolean = false,
)
