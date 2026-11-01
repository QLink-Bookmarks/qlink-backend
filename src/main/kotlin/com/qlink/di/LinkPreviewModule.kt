package com.qlink.di

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.qlink.config.LinkPreviewConfig
import com.qlink.link.client.LambdaLinkPreviewSyncClient
import com.qlink.link.client.LinkPreviewSyncClient
import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

fun linkPreviewModule(config: ApplicationConfig) =
    module {
        single { LinkPreviewConfig.from(config) }

        single {
            val previewConfig = get<LinkPreviewConfig>()
            LambdaClient {
                region = previewConfig.region
                previewConfig.endpoint?.let { endpoint ->
                    endpointUrl = Url.parse(endpoint)
                    credentialsProvider = StaticCredentialsProvider(Credentials("local", "local"))
                }
            }
        }

        single<LinkPreviewSyncClient> {
            LambdaLinkPreviewSyncClient(
                lambdaClient = get(),
                config = get(),
            )
        }
    }
