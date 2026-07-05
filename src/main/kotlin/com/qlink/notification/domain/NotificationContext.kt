package com.qlink.notification.domain

enum class NotificationContext {
    TODO,
    ANNOUNCE,
    ;

    companion object {
        fun from(value: String): NotificationContext? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
