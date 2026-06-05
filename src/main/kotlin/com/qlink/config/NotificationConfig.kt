package com.qlink.config

import io.ktor.server.config.ApplicationConfig

private const val DEFAULT_EXPO_PUSH_SEND_URL = "https://exp.host/--/api/v2/push/send"

data class NotificationConfig(
    val expo: ExpoConfig,
    val fcm: FcmConfig,
) {
    data class ExpoConfig(
        val sendUrl: String,
        val accessToken: String?,
    )

    data class FcmConfig(
        val serviceAccountJson: String?,
    )

    companion object {
        fun from(
            config: ApplicationConfig,
            env: Map<String, String> = System.getenv(),
        ): NotificationConfig =
            NotificationConfig(
                expo =
                    ExpoConfig(
                        sendUrl =
                            env.secretOrNull("EXPO_PUSH_SEND_URL")
                                ?: config.optionalSecret("notification.expo.sendUrl")
                                ?: DEFAULT_EXPO_PUSH_SEND_URL,
                        accessToken =
                            env.secretOrNull("EXPO_ACCESS_TOKEN")
                                ?: config.optionalSecret("notification.expo.accessToken"),
                    ),
                fcm =
                    FcmConfig(
                        serviceAccountJson =
                            env.secretOrNull("FCM_SERVICE_ACCOUNT_JSON")
                                ?: config.optionalSecret("notification.fcm.serviceAccountJson"),
                    ),
            )
    }
}

private fun Map<String, String>.secretOrNull(key: String): String? = get(key)?.takeIf(String::isNotBlank)

private fun ApplicationConfig.optionalSecret(path: String): String? = optionalString(path)?.takeIf(String::isNotBlank)
