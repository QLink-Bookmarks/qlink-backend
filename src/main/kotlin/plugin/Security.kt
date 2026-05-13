package com.qlink.plugin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.config.SecurityConfig
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
  val config by inject<SecurityConfig>()

  authentication {
    jwt {
      realm = config.jwtRealm
      verifier(
        JWT
          .require(Algorithm.HMAC256(config.jwtSecret))
          .withAudience(config.jwtAudience)
          .withIssuer(config.jwtDomain)
          .build(),
      )
      validate { credential ->
        if (credential.payload.audience.contains(
            config.jwtAudience,
          )
        ) {
          JWTPrincipal(credential.payload)
        } else {
          null
        }
      }
    }
  }
}
