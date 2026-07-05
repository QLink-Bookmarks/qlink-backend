package com.qlink.plugin

import com.qlink.ai.route.aiRoutes
import com.qlink.auth.domain.JwtPrincipal
import com.qlink.auth.domain.Role
import com.qlink.auth.route.authRoutes
import com.qlink.device.route.deviceRoutes
import com.qlink.folder.route.folderRoutes
import com.qlink.image.route.imageRoutes
import com.qlink.link.route.linkRoutes
import com.qlink.notification.route.notificationRoutes
import com.qlink.post.route.postRoutes
import com.qlink.todo.route.todoRoutes
import com.qlink.user.route.userRoutes
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private val ROOT_MESSAGE =
    """
    Hello ALink! Please find details from below.
    OpenAPI: /openapi
    Redoc: /redoc

    I would be glad if my service may help you, but kindly move back if you are trying something bad with my service :(
    """.trimIndent()

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText(ROOT_MESSAGE)
        }
        get("/health") {
            call.respond(mapOf("status" to "HEALTHY"))
        }

        route("/api") {
            aiRoutes()
            authRoutes()
            deviceRoutes()
            folderRoutes()
            imageRoutes()
            linkRoutes()
            notificationRoutes()
            postRoutes()
            todoRoutes()
            userRoutes()
        }
    }
}

fun ApplicationCall.jwtPrincipalOrGuest(): JwtPrincipal = principal<JwtPrincipal>() ?: JwtPrincipal(userId = 0, role = Role.GUEST)
