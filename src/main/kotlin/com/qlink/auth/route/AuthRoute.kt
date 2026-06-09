package com.qlink.auth.route

import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.NativeRefreshTokenRequest
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.service.RefreshAuthTokenService
import com.qlink.auth.service.SignInService
import com.qlink.common.cookie.appendRefreshTokenCookie
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.respondError
import com.qlink.common.response.respondSuccess
import io.github.smiley4.ktoropenapi.post as postWithoutResource
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.resource
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

const val REFRESH_TOKEN_HEADER_NAME = "refresh_token"
const val CSRF_TOKEN_HEADER_NAME = "csrf_token"

fun Route.authRoutes() {
    val signInService by inject<SignInService>()
    val refreshAuthTokenService by inject<RefreshAuthTokenService>()

    post<AuthResources.Sign>(signInDocs()) {
        val request = call.receive<SignInRequest>()
        val response = signInService.signIn(request)

        if (request.platform == AuthPlatform.WEB) {
            call.appendRefreshTokenCookie(response.refreshToken)
        }

        call.respondSuccess(HttpStatusCode.Created, response)
    }

    webRefreshRoute(refreshAuthTokenService)

    post<AuthResources.Token.Refresh.Native>(nativeRefreshAuthTokenDocs()) {
        val request = call.receive<NativeRefreshTokenRequest>()
        val response = refreshAuthTokenService.refresh(request.requireRefreshToken())

        call.respondSuccess(HttpStatusCode.Created, response)
    }
}

private fun Route.webRefreshRoute(refreshAuthTokenService: RefreshAuthTokenService) {
    resource<AuthResources.Token.Refresh.Web> {
        install(CSRF) {
            checkHeader(CSRF_TOKEN_HEADER_NAME)
            onFailure { reason ->
                respondError(
                    errorCode = ErrorCode.AUTH_CSRF_TOKEN_INVALID,
                    causeMessage = reason,
                )
            }
        }

        postWithoutResource(webRefreshAuthTokenDocs()) {
            val refreshToken =
                call.request.headers[REFRESH_TOKEN_HEADER_NAME]
                    ?.takeIf { it.isNotBlank() }
                    ?: throw BusinessException(ErrorCode.AUTH_NO_CREDENTIALS)
            val response = refreshAuthTokenService.refresh(refreshToken)

            call.appendRefreshTokenCookie(response.refreshToken)
            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
