package com.qlink.di

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.qlink.bookmarkimport.service.CountImportThumbnailTargetsService
import com.qlink.bookmarkimport.service.FillImportThumbnailService
import com.qlink.bookmarkimport.service.GetImportThumbnailTargetsService
import com.qlink.bookmarkimport.worker.ImportThumbnailWorker
import com.qlink.config.LinkPreviewConfig
import com.qlink.config.MonitoringConfig
import com.qlink.config.WorkerConfig
import com.qlink.link.client.LambdaLinkPreviewSyncClient
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.link.service.FillLinkPreviewService
import com.qlink.link.worker.LinkPreviewFillSignal
import com.qlink.link.worker.LinkPreviewFillWorker
import io.ktor.server.config.ApplicationConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module
import org.slf4j.LoggerFactory

fun linkPreviewModule(config: ApplicationConfig) =
    module {
        single { LinkPreviewConfig.from(config) }

        single { WorkerConfig.from(config) }

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
                meterRegistry = get<PrometheusMeterRegistry>(),
                log = LoggerFactory.getLogger(LambdaLinkPreviewSyncClient::class.java),
            )
        }

        single {
            LinkPreviewFillSignal()
        }

        single {
            FillLinkPreviewService(
                tx = get(),
                linkRepository = get(),
                previewClient = get(),
                urlResolver = get(),
            )
        }

        single {
            LinkPreviewFillWorker(
                signal = get(),
                fillService = get(),
                traceIdLength = get<MonitoringConfig>().httpLogging.traceIdLength,
                log = LoggerFactory.getLogger(LinkPreviewFillWorker::class.java),
            )
        }

        single {
            FillImportThumbnailService(
                tx = get(),
                bookmarkImportRepository = get(),
                linkRepository = get(),
                previewClient = get(),
                urlResolver = get(),
            )
        }

        single {
            GetImportThumbnailTargetsService(
                tx = get(),
                bookmarkImportRepository = get(),
            )
        }

        single {
            CountImportThumbnailTargetsService(
                tx = get(),
                bookmarkImportRepository = get(),
            )
        }

        single {
            ImportThumbnailWorker(
                signal = get(),
                getTargetsService = get(),
                countTargetsService = get(),
                fillService = get(),
                config = get(),
                meterRegistry = get<PrometheusMeterRegistry>(),
                traceIdLength = get<MonitoringConfig>().httpLogging.traceIdLength,
                log = LoggerFactory.getLogger(ImportThumbnailWorker::class.java),
            )
        }
    }
