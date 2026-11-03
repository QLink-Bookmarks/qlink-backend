package com.qlink.common.text

import java.net.URI

private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)

fun URI.hasSchemeIn(schemes: Set<String>): Boolean = scheme?.lowercase() in schemes

fun String.toNormalizedUrlOrNull(
    ignoredQueryKeys: Set<String>,
    ignoredQueryKeyPrefixes: Set<String>,
): String? {
    val lowercaseKeys = ignoredQueryKeys.mapTo(HashSet()) { it.lowercase() }
    val lowercaseKeyPrefixes = ignoredQueryKeyPrefixes.mapTo(HashSet()) { it.lowercase() }

    return runCatching { URI(trim()) }
        .getOrNull()
        ?.takeIf { it.scheme != null && it.host != null }
        ?.let { uri ->
            val scheme = uri.scheme.lowercase()
            val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
            val port =
                uri.port
                    .takeUnless { it == -1 || it == DEFAULT_PORTS[scheme] }
                    ?.let { ":$it" }
                    .orEmpty()
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val query =
                uri.rawQuery
                    ?.split('&')
                    ?.filter { it.isNotEmpty() && !it.hasQueryKeyIn(lowercaseKeys, lowercaseKeyPrefixes) }
                    ?.sortedBy { it.substringBefore('=') }
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "&", prefix = "?")
                    .orEmpty()

            "$scheme://$userInfo${uri.host.lowercase()}$port$path$query"
        }
}

private fun String.hasQueryKeyIn(
    keys: Set<String>,
    keyPrefixes: Set<String>,
): Boolean =
    substringBefore('=')
        .lowercase()
        .let { key -> key in keys || keyPrefixes.any(key::startsWith) }
