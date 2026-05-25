package com.qlink.common.search

import kotlinx.serialization.Serializable

@Serializable
enum class SearchOrder {
    LATEST,
    EARLIEST,
    LAXICO,
    SIMILAR,
    ;

    companion object {
        fun from(value: String): SearchOrder? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class SearchCursor<T>(
    val order: SearchOrder,
    val value: T,
)
