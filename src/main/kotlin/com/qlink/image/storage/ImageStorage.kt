package com.qlink.image.storage

import com.qlink.image.domain.ImagePurpose

interface ImageStorage {
    suspend fun upload(
        purpose: ImagePurpose,
        key: String,
        bytes: ByteArray,
        contentType: String,
    )
}
