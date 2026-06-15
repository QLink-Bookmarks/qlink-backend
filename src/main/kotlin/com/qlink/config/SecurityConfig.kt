package com.qlink.config

import io.ktor.server.config.ApplicationConfig

data class SecurityConfig(
    val jwtSecret: String,
    val accessDurationSeconds: Int = 900,
    val refreshDurationSeconds: Int = 1_209_600,
) {
    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): SecurityConfig =
            SecurityConfig(
                jwtSecret =
                    env.secretOrNull("JWT_SECRET")
                        ?: config.string("security.jwt.secret"),
                accessDurationSeconds = config.optionalInt("security.access.duration") ?: 900,
                refreshDurationSeconds = config.optionalInt("security.refresh.duration") ?: 1_209_600,
            )
    }
}

private fun Map<String, String>.secretOrNull(key: String): String? = get(key)?.takeIf(String::isNotBlank)
