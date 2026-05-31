package com.qlink.plugin

object LogSanitizer {
    private const val TOO_LARGE = "<skipped:too-large>"

    fun sanitize(
        value: String,
        sensitiveKeys: List<String>,
        maxLength: Int,
    ): String {
        if (value.length > maxLength) {
            return TOO_LARGE
        }

        return sensitiveKeys.fold(value) { masked, key ->
            masked
                .maskJsonValue(key)
                .maskQueryValue(key)
                .maskHeaderValue(key)
        }
    }

    private fun String.maskJsonValue(key: String): String {
        val escapedKey = Regex.escape(key)
        val pattern =
            Regex(
                pattern = """("(?i:$escapedKey)"\s*:\s*")([^"]*)(")""",
            )
        return replace(pattern, "$1***$3")
    }

    private fun String.maskQueryValue(key: String): String {
        val escapedKey = Regex.escape(key)
        val pattern =
            Regex(
                pattern = """((?:^|[?&])(?i:$escapedKey)=)([^&]*)""",
            )
        return replace(pattern, "$1***")
    }

    private fun String.maskHeaderValue(key: String): String {
        val escapedKey = Regex.escape(key)
        val pattern =
            Regex(
                pattern = """((?i:$escapedKey)\s*[:=]\s*)([^\s,;]*)""",
            )
        return replace(pattern, "$1***")
    }
}
