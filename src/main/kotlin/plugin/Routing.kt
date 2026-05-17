package com.qlink.plugin

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.auth.domain.Role
import com.qlink.link.route.linkRoutes
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        authenticate {
            get("/sample/jwt", {
                summary = "Required JWT sample"
                description = "Returns the JWT principal parsed from a valid bearer token."
                response {
                    code(HttpStatusCode.OK) {
                        description = "The JWT principal parsed from the bearer token."
                        body<JwtPrincipal>()
                    }
                }
            }) {
                val principal = call.principal<JwtPrincipal>()!!
                call.respond(principal)
            }
        }
        authenticate(optional = true) {
            get("/sample/jwt-optional", {
                summary = "Optional JWT sample"
                description = "Returns the JWT principal or a guest principal."
                response {
                    code(HttpStatusCode.OK) {
                        description =
                            "The JWT principal, or a guest principal when no bearer token is provided."
                        body<JwtPrincipal>()
                    }
                }
            }) {
                val principal = call.jwtPrincipalOrGuest()
                call.respond(principal)
            }
        }

        route("/api") {
            linkRoutes()
        }
    }
}

fun ApplicationCall.jwtPrincipalOrGuest(): JwtPrincipal =
    principal<JwtPrincipal>() ?: JwtPrincipal(userId = 0, role = Role.GUEST)
