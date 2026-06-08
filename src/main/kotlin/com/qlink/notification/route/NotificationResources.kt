package com.qlink.notification.route

import com.qlink.notification.dto.DEFAULT_NOTIFICATION_SCROLL_SIZE
import com.qlink.notification.dto.DEFAULT_NOTIFICATION_SEARCH_ORDER
import io.ktor.resources.Resource

@Resource("/notifications")
class NotificationResources(
    val query: String? = null,
    val order: String = DEFAULT_NOTIFICATION_SEARCH_ORDER,
    val cursor: String? = null,
    val size: Int = DEFAULT_NOTIFICATION_SCROLL_SIZE,
) {
    @Resource("unread")
    class Unread(
        val parent: NotificationResources = NotificationResources(),
    )

    @Resource("{id}")
    class ById(
        val parent: NotificationResources = NotificationResources(),
        val id: Long,
    ) {
        @Resource("read")
        class Read(
            val parent: ById,
        )
    }
}
