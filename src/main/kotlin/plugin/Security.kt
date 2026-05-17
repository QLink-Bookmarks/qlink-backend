package com.qlink.plugin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.auth.domain.JwtPrincipal
import com.qlink.auth.domain.Role
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.respondError
import com.qlink.config.SecurityConfig
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val config by inject<SecurityConfig>()

    authentication {
        jwt {
            realm = "QLINK-REST"

            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.jwtSecret))
                    .build(),
            )

            validate { credential ->
                val userId = credential.payload.subject?.toLongOrNull()
                val role =
                    credential.payload.getClaim("role").asString()?.let { role ->
                        runCatching { Role.valueOf(role) }.getOrNull()
                    }

                if (userId != null && role != null) {
                    JwtPrincipal(userId, role)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                val cause = call.authentication.allFailures.firstOrNull()

                call.response.headers.append(
                    HttpHeaders.WWWAuthenticate,
                    "$defaultScheme realm=\"$realm\"",
                )

                val errorCode =
                    when (cause) {
                        is AuthenticationFailedCause.NoCredentials -> ErrorCode.AUTH_NO_CREDENTIALS
                        is AuthenticationFailedCause.InvalidCredentials -> ErrorCode.AUTH_INVALID_CREDENTIALS
                        is AuthenticationFailedCause.Error -> ErrorCode.AUTH_WRONG_CREDENTIALS
                        else -> ErrorCode.AUTH_UNEXPECTED_CREDENTIALS
                    }

                call.respondError(
                    errorCode,
                    causeName = cause?.let { it::class.simpleName },
                    causeMessage = (cause as? AuthenticationFailedCause.Error)?.message,
                )
            }
        }
    }
}
