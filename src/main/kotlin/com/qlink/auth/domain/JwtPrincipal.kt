package com.qlink.auth.domain

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import kotlinx.serialization.Serializable

@Serializable
data class JwtPrincipal(
    val userId: Long,
    val role: Role,
)
