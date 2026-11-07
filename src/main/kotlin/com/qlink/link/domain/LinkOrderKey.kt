package com.qlink.link.domain

import com.qlink.common.search.SortDirection

enum class LinkOrderKey(
    val defaultDirection: SortDirection,
    val directions: Set<SortDirection>,
) {
    CREATED_AT(defaultDirection = SortDirection.DESC, directions = setOf(SortDirection.ASC, SortDirection.DESC)),
    TITLE(defaultDirection = SortDirection.ASC, directions = setOf(SortDirection.ASC, SortDirection.DESC)),
    SIMILARITY(defaultDirection = SortDirection.DESC, directions = setOf(SortDirection.DESC)),
    ;

    companion object {
        fun from(value: String): LinkOrderKey? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
