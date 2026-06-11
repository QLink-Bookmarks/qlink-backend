package com.qlink.di

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.qlink.config.S3Config
import com.qlink.image.storage.ImageStorage
import com.qlink.image.storage.S3ImageStorage
import org.koin.dsl.module

fun storageModule(config: S3Config) =
    module {
        single { config }

        single {
            buildS3Client(config)
        }

        single<ImageStorage> {
            S3ImageStorage(
                s3Client = get(),
                config = config,
            )
        }
    }

private fun buildS3Client(config: S3Config): S3Client =
    S3Client {
        region = config.region
        forcePathStyle = config.forcePathStyle
        config.endpoint?.let { endpointUrl = Url.parse(it) }

        if (config.accessKeyId != null && config.secretAccessKey != null) {
            credentialsProvider =
                StaticCredentialsProvider(
                    Credentials(
                        accessKeyId = config.accessKeyId,
                        secretAccessKey = config.secretAccessKey,
                    ),
                )
        }
    }
