package com.qlink.common.scroll

import kotlinx.serialization.Serializable

@Serializable
data class ScrollResponse<T>(
    val isEmpty: Boolean,
    val contents: List<T>,
    val nextCursor: String? = null,
    val hasNext: Boolean,
)
