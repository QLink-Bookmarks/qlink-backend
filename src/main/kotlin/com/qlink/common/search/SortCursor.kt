package com.qlink.common.search

import kotlinx.serialization.Serializable

@Serializable
data class SortCursor<K, V>(
    val orderKey: K? = null,
    val direction: SortDirection? = null,
    val order: SearchOrder? = null,
    val value: V,
)
