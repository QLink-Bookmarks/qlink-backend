package com.qlink.image.domain

enum class ImageType(
    val contentType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    ;

    companion object {
        fun detect(bytes: ByteArray): ImageType? =
            when {
                bytes.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
                bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
                bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> GIF
                bytes.isWebp() -> WEBP
                else -> null
            }

        private fun ByteArray.startsWith(vararg signature: Int): Boolean {
            if (size < signature.size) return false
            return signature.withIndex().all { (index, expected) -> this[index].toInt() and 0xFF == expected }
        }

        private fun ByteArray.isWebp(): Boolean =
            startsWith(0x52, 0x49, 0x46, 0x46) &&
                size >= 12 &&
                this[8].toInt() and 0xFF == 0x57 &&
                this[9].toInt() and 0xFF == 0x45 &&
                this[10].toInt() and 0xFF == 0x42 &&
                this[11].toInt() and 0xFF == 0x50
    }
}
