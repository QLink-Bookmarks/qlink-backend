package com.qlink.config

import io.ktor.server.config.ApplicationConfig

data class AppleConfig(
    val clientIds: List<String>,
) {
    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): AppleConfig =
            AppleConfig(
                clientIds =
                    env.clientIdsOrNull("APPLE_CLIENT_IDS")
                        ?: config.optionalStringList("apple.clientIds"),
            )
    }
}

private fun Map<String, String>.clientIdsOrNull(key: String): List<String>? =
    get(key)
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.takeIf(List<String>::isNotEmpty)
