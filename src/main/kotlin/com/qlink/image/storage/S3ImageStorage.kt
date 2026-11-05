package com.qlink.image.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.S3Config
import com.qlink.image.domain.ImagePurpose

class S3ImageStorage(
    private val s3Client: S3Client,
    private val config: S3Config,
) : ImageStorage {
    override suspend fun upload(
        purpose: ImagePurpose,
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        s3Client.putObject {
            this.bucket = bucketOf(purpose)
            this.key = key
            this.body = ByteStream.fromBytes(bytes)
            this.contentType = contentType
        }
    }

    private fun bucketOf(purpose: ImagePurpose): String =
        when (purpose) {
            ImagePurpose.PROFILE -> config.profileBucket
            ImagePurpose.POST -> config.postBucket
            ImagePurpose.LINK_PREVIEW -> config.previewBucket
            ImagePurpose.FAVICON -> throw BusinessException(ErrorCode.IMAGE_STORAGE_PURPOSE_UNSUPPORTED)
        }
}
