package com.qlink.di

import com.qlink.config.LinkContentConfig
import com.qlink.config.WorkerConfig
import com.qlink.linkcontent.client.LambdaLinkContentSyncClient
import com.qlink.linkcontent.client.LinkContentSyncClient
import com.qlink.linkcontent.service.CollectLinkContentService
import com.qlink.linkcontent.service.GetLinkContentScheduleService
import com.qlink.linkcontent.service.GetLinkContentTargetsService
import com.qlink.linkcontent.worker.LinkContentSignal
import com.qlink.linkcontent.worker.LinkContentWorker
import io.ktor.server.config.ApplicationConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module
import org.slf4j.LoggerFactory

fun linkContentModule(config: ApplicationConfig) =
    module {
        single { LinkContentConfig.from(config) }

        single {
            LinkContentSignal()
        }

        single<LinkContentSyncClient> {
            LambdaLinkContentSyncClient(
                lambdaClient = get(),
                config = get(),
                meterRegistry = get<PrometheusMeterRegistry>(),
                log = LoggerFactory.getLogger(LambdaLinkContentSyncClient::class.java),
            )
        }

        single {
            CollectLinkContentService(
                tx = get(),
                linkContentRepository = get(),
                linkRepository = get(),
                contentClient = get(),
            )
        }

        single {
            GetLinkContentTargetsService(
                tx = get(),
                linkContentRepository = get(),
            )
        }

        single {
            GetLinkContentScheduleService(
                tx = get(),
                linkContentRepository = get(),
            )
        }

        single {
            LinkContentWorker(
                signal = get(),
                getTargetsService = get(),
                getScheduleService = get(),
                collectService = get(),
                config = get(),
                pollInterval = get<WorkerConfig>().pollInterval,
                meterRegistry = get<PrometheusMeterRegistry>(),
                log = LoggerFactory.getLogger(LinkContentWorker::class.java),
            )
        }
    }
