package com.qlink.notification.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.common.scroll.ScrollRequest
import com.qlink.notification.service.GetNotificationsService
import com.qlink.notification.service.GetUnreadNotificationCountService
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.notificationRoutes() {
    val getNotificationsService by inject<GetNotificationsService>()
    val getUnreadNotificationCountService by inject<GetUnreadNotificationCountService>()

    authenticate {
        get<NotificationResources>(getNotificationsDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response =
                getNotificationsService.getNotifications(
                    loginId = principal.userId,
                    query = resource.query,
                    order = resource.order,
                    scrollRequest =
                        ScrollRequest(
                            cursor = resource.cursor,
                            size = resource.size,
                        ),
                )

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        get<NotificationResources.Unread>(getUnreadNotificationCountDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getUnreadNotificationCountService.getUnreadCount(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
