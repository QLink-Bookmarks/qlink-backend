package com.qlink.image.service

import com.qlink.config.S3Config

class PreviewImageUrlResolver(
    private val config: S3Config,
) {
    val environment: String
        get() = config.environment

    fun publicUrl(key: String): String = "${config.publicBaseUrl.trimEnd('/')}/$key"

    fun previewUrl(key: String): String? = key.takeIf { it.matches(keyPattern("preview")) }?.let(::publicUrl)

    fun faviconUrl(key: String): String? = key.takeIf { it.matches(keyPattern("favicon")) }?.let(::publicUrl)

    private fun keyPattern(kind: String): Regex = Regex("^${config.environment}-$kind-[0-9a-f]{64}\\.(?:jpg|jpeg|png|gif|webp)$")
}
