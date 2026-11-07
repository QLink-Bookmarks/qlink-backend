package com.qlink.common.search

import kotlinx.serialization.Serializable

@Serializable
enum class SortDirection {
    ASC,
    DESC,
    ;

    companion object {
        fun from(value: String): SortDirection? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
