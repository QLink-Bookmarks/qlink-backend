package com.qlink.todo.domain

enum class TodoStatus {
    ACTIVE,
    OVERDUE,
    COMPLETED,
    ;

    companion object {
        fun from(value: String): TodoStatus? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
