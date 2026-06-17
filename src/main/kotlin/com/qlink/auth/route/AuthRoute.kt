package com.qlink.auth.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.NativeRefreshTokenRequest
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.dto.SignOutRequest
import com.qlink.auth.service.RefreshAuthTokenService
import com.qlink.auth.service.SignInService
import com.qlink.auth.service.SignOutService
import com.qlink.common.cookie.REFRESH_TOKEN_COOKIE_NAME
import com.qlink.common.cookie.appendRefreshTokenCookie
import com.qlink.common.cookie.expireAuthCookies
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.respondError
import com.qlink.common.response.respondSuccess
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.resources.resource
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import io.github.smiley4.ktoropenapi.delete as deleteWithoutResource
import io.github.smiley4.ktoropenapi.post as postWithoutResource

const val REFRESH_TOKEN_HEADER_NAME = "refresh_token"
const val CSRF_TOKEN_HEADER_NAME = "csrf_token"

fun Route.authRoutes() {
    val signInService by inject<SignInService>()
    val refreshAuthTokenService by inject<RefreshAuthTokenService>()
    val signOutService by inject<SignOutService>()

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

    signOutRoute(signOutService)
}

private fun Route.signOutRoute(signOutService: SignOutService) {
    authenticate {
        resource<AuthResources.Signout> {
            installCsrfHeaderCheck()

            deleteWithoutResource(signOutDocs()) {
                val principal = call.principal<JwtPrincipal>()!!
                val refreshToken = call.resolveSignOutRefreshToken()

                signOutService.signOut(principal.userId, refreshToken)

                call.expireAuthCookies()
                call.respondSuccess(HttpStatusCode.OK)
            }
        }
    }
}

private suspend fun ApplicationCall.resolveSignOutRefreshToken(): String? =
    request.cookies[REFRESH_TOKEN_COOKIE_NAME]?.takeIf { it.isNotBlank() }
        ?: runCatching { receiveNullable<SignOutRequest>()?.refreshToken }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

private fun Route.webRefreshRoute(refreshAuthTokenService: RefreshAuthTokenService) {
    resource<AuthResources.Token.Refresh.Web> {
        installCsrfHeaderCheck()

        postWithoutResource(webRefreshAuthTokenDocs()) {
            val refreshToken =
                call.request.cookies[REFRESH_TOKEN_COOKIE_NAME]?.takeIf { it.isNotBlank() }
                    ?: call.request.headers[REFRESH_TOKEN_HEADER_NAME]?.takeIf { it.isNotBlank() }
                    ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISSING)
            val response = refreshAuthTokenService.refresh(refreshToken)

            call.appendRefreshTokenCookie(response.refreshToken)
            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}

private fun Route.installCsrfHeaderCheck() {
    install(CSRF) {
        checkHeader(CSRF_TOKEN_HEADER_NAME)
        onFailure { reason ->
            respondError(
                errorCode = ErrorCode.AUTH_CSRF_TOKEN_INVALID,
                causeMessage = reason,
            )
        }
    }
}
