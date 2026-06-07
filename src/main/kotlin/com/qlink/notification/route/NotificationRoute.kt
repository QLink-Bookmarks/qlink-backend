package com.qlink.notification.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.notification.service.GetUnreadNotificationCountService
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.notificationRoutes() {
    val getUnreadNotificationCountService by inject<GetUnreadNotificationCountService>()

    authenticate {
        get<NotificationResources.Unread>(getUnreadNotificationCountDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getUnreadNotificationCountService.getUnreadCount(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
