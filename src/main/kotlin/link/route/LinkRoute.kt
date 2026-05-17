package com.qlink.link.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.service.CreateLinkService
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.linkRoutes() {
    val createLinkService by inject<CreateLinkService>()

    route("/links") {
        authenticate {
            post({
                summary = "링크 생성 API"
                request { body<CreateLinkRequest>() }
                response {
                    code(HttpStatusCode.Created) {
                        description = "링크 생성 성공"
                        body<CreateLinkResponse>()
                    }
                }
            }) {
                val principal = call.principal<JwtPrincipal>()!!
                val request = call.receive<CreateLinkRequest>()
                val response = createLinkService.createLink(principal.userId, request)

                call.respondSuccess(HttpStatusCode.Created, response)
            }
        }
    }
}