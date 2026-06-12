package com.qlink.image.storage

interface ImageStorage {
    suspend fun upload(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ): String
}
