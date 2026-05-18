package com.qlink.config

data class MonitoringConfig(
    val metricsPath: String,
    val httpLogging: HttpLoggingConfig,
)

data class HttpLoggingConfig(
    val slowThresholdMs: Long,
    val maxLength: Int,
    val traceIdLength: Int,
    val sensitiveKeys: List<String>,
)
