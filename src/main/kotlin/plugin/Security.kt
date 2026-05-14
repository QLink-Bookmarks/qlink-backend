package com.qlink.plugin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.auth.domain.JwtPrincipal
import com.qlink.auth.domain.Role
import com.qlink.config.SecurityConfig
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
  val config by inject<SecurityConfig>()

  authentication {
    jwt {
      verifier(
        JWT
          .require(Algorithm.HMAC256(config.jwtSecret))
          .build(),
      )
      validate { credential ->
        val userId = credential.payload.subject?.toLongOrNull()
        val role = credential.payload.getClaim("role").asString()?.let { role ->
          runCatching { Role.valueOf(role) }.getOrNull()
        }

        if (userId != null && role != null) {
          JwtPrincipal(userId, role)
        } else {
          null
        }
      }
    }
  }
}
