package com.qlink.common.scroll

import kotlinx.serialization.Serializable

@Serializable
data class ScrollRequest(
    val cursor: String? = null,
    val size: Int = DEFAULT_SCROLL_SIZE,
)

const val DEFAULT_SCROLL_SIZE = 15
