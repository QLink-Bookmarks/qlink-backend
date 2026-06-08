package com.qlink.config

data class SecurityConfig(
    val jwtSecret: String,
    val accessDurationSeconds: Int = 900,
    val refreshDurationSeconds: Int = 1_209_600,
)
