package com.qlink.config

data class FlywayConfig(
    val schema: String,
    val locations: List<String>,
)
