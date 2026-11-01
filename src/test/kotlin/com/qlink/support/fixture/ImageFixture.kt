package com.qlink.support.fixture

import com.qlink.image.domain.ImageType
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

object ImageFixture {
    private const val WEBP_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA=="
    private const val HEADER_LENGTH = 16

    fun validImage(type: ImageType = ImageType.entries.random()): ByteArray =
        when (type) {
            ImageType.JPEG -> encode("jpeg")
            ImageType.PNG -> encode("png")
            ImageType.GIF -> encode("gif")
            ImageType.WEBP -> Base64.getDecoder().decode(WEBP_BASE64)
        }

    fun validPngOfSize(size: Int): ByteArray = encode("png").let { it + ByteArray(size - it.size) }

    fun headerOnlyImage(type: ImageType): ByteArray = validImage(type).let { it.copyOf(minOf(HEADER_LENGTH, it.size - 1)) }

    fun ico(): ByteArray = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x10, 0x10, 0x00, 0x00, 0x01, 0x00, 0x20, 0x00)

    fun svg(): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16"/></svg>""".toByteArray()

    private fun encode(format: String): ByteArray =
        ByteArrayOutputStream()
            .also { ImageIO.write(randomPixels(), format, it) }
            .toByteArray()

    private fun randomPixels(): BufferedImage {
        val width = RandomFixture.randomInt(min = 8, max = 32)
        val height = RandomFixture.randomInt(min = 8, max = 32)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    setRGB(x, y, RandomFixture.randomInt(min = 0, max = 0xFFFFFF))
                }
            }
        }
    }
}
