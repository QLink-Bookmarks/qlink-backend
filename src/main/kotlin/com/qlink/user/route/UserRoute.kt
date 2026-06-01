package com.qlink.user.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.user.service.GetMyProfileService
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileService by inject<GetMyProfileService>()

    authenticate {
        get<UserResources.Me>(getMyProfileDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getMyProfileService.getMyProfile(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
