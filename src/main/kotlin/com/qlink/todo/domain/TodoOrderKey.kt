package com.qlink.todo.domain

import com.qlink.common.search.SortDirection

enum class TodoOrderKey(
    val defaultDirection: SortDirection,
) {
    REMINDER_AT(SortDirection.ASC),
    CREATED_AT(SortDirection.DESC),
    COMPLETED_AT(SortDirection.DESC),
    ;

    companion object {
        fun from(value: String): TodoOrderKey? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
