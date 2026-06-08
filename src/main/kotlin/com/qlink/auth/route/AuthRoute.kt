package com.qlink.auth.route

import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.service.SignInService
import com.qlink.common.cookie.appendRefreshTokenCookie
import com.qlink.common.response.respondSuccess
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val signInService by inject<SignInService>()

    post<AuthResources.Sign>(signInDocs()) {
        val request = call.receive<SignInRequest>()
        val response = signInService.signIn(request)

        if (request.platform == AuthPlatform.WEB) {
            call.appendRefreshTokenCookie(response.refreshToken)
        }

        call.respondSuccess(HttpStatusCode.Created, response)
    }
}
