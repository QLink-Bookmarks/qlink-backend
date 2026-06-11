package com.qlink.image.storage

/**
 * Stores image bytes and returns a publicly accessible URL.
 */
interface ImageStorage {
    suspend fun upload(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ): String
}
