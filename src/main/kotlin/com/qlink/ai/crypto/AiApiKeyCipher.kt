package com.qlink.ai.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AiApiKeyCipher(
    keyBase64: String,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val keyBytes = Base64.getDecoder().decode(keyBase64)
    private val secretKey = SecretKeySpec(keyBytes.also { require(it.size == KEY_SIZE_BYTES) }, "AES")

    fun encrypt(plainText: String): String {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, nonce))
        val ciphertextWithTag = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return listOf(
            VERSION,
            Base64.getEncoder().encodeToString(nonce),
            Base64.getEncoder().encodeToString(ciphertextWithTag),
        ).joinToString(":")
    }

    fun decrypt(cipherText: String): String? =
        runCatching {
            val parts = cipherText.split(":")
            if (parts.size != 3 || parts[0] != VERSION) {
                return null
            }

            val nonce = Base64.getDecoder().decode(parts[1])
            val ciphertextWithTag = Base64.getDecoder().decode(parts[2])
            if (nonce.size != NONCE_SIZE_BYTES) {
                return null
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, nonce))

            cipher.doFinal(ciphertextWithTag).toString(Charsets.UTF_8)
        }.getOrNull()

    companion object {
        private const val VERSION = "v1"
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
