package com.qlink.image.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import com.qlink.config.S3Config

class S3ImageStorage(
    private val s3Client: S3Client,
    private val config: S3Config,
) : ImageStorage {
    override suspend fun upload(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ): String {
        s3Client.putObject {
            this.bucket = config.bucket
            this.key = key
            this.body = ByteStream.fromBytes(bytes)
            this.contentType = contentType
        }

        return publicUrl(key)
    }

    private fun publicUrl(key: String): String {
        config.publicBaseUrl?.let { base -> return "${base.trimEnd('/')}/$key" }
        config.endpoint?.let { endpoint -> return "${endpoint.trimEnd('/')}/${config.bucket}/$key" }
        return "https://${config.bucket}.s3.${config.region}.amazonaws.com/$key"
    }
}
