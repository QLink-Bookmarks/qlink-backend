package com.qlink.common.cookie

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall

const val REFRESH_TOKEN_COOKIE_NAME = "refresh_token"
const val REFRESH_TOKEN_COOKIE_PATH = "/api/auth"
const val CSRF_TOKEN_COOKIE_NAME = "csrf_token"

fun ApplicationCall.appendRefreshTokenCookie(refreshToken: String) {
    response.cookies.append(
        Cookie(
            name = REFRESH_TOKEN_COOKIE_NAME,
            value = refreshToken,
            path = REFRESH_TOKEN_COOKIE_PATH,
            httpOnly = true,
            secure = true,
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

fun ApplicationCall.expireAuthCookies() {
    response.cookies.append(
        Cookie(
            name = REFRESH_TOKEN_COOKIE_NAME,
            value = "",
            path = REFRESH_TOKEN_COOKIE_PATH,
            maxAge = 0,
            httpOnly = true,
            secure = true,
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
    response.cookies.append(
        Cookie(
            name = CSRF_TOKEN_COOKIE_NAME,
            value = "",
            path = "/",
            maxAge = 0,
        ),
    )
}
