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

// deprecated된 appendExpired 대신 동일 속성으로 빈 쿠키를 덮어쓰고,
// maxAge = 0 (Max-Age=0)으로 브라우저가 즉시 삭제하도록 한다
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
