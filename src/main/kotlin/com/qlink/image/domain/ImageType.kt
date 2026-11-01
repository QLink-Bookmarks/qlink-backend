package com.qlink.image.domain

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader

private const val MAX_DECODED_DIMENSION = 1024

enum class ImageType(
    val contentType: String,
    val extension: String,
    private val formatName: String,
) {
    JPEG("image/jpeg", "jpg", "jpeg"),
    PNG("image/png", "png", "png"),
    GIF("image/gif", "gif", "gif"),
    WEBP("image/webp", "webp", "webp"),
    ;

    companion object {
        fun detect(bytes: ByteArray): ImageType? = signatureOf(bytes)?.takeIf { it.decodes(bytes) }

        private fun signatureOf(bytes: ByteArray): ImageType? =
            when {
                bytes.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
                bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
                bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> GIF
                bytes.isWebp() -> WEBP
                else -> null
            }

        private fun ImageType.decodes(bytes: ByteArray): Boolean =
            runCatching {
                ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                    ImageIO
                        .getImageReaders(input)
                        .asSequence()
                        .firstOrNull { reader -> reader.originatingProvider.formatNames.any { it.equals(formatName, ignoreCase = true) } }
                        ?.let { reader ->
                            try {
                                reader.input = input
                                reader.decodesWithoutWarning()
                            } finally {
                                reader.dispose()
                            }
                        } ?: false
                }
            }.getOrDefault(false)

        private fun ImageReader.decodesWithoutWarning(): Boolean {
            var warned = false
            addIIOReadWarningListener { _, _ -> warned = true }
            val step = maxOf(1, maxOf(getWidth(0), getHeight(0)) / MAX_DECODED_DIMENSION)
            val param = defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            read(0, param)
            return !warned
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
