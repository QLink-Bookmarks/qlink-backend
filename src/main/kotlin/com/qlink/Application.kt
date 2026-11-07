package com.qlink

import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.bookmarkimport.worker.ImportThumbnailWorker
import com.qlink.link.worker.LinkPreviewFillWorker
import com.qlink.linkcontent.worker.LinkContentWorker
import com.qlink.notification.worker.TaskScheduler
import com.qlink.plugin.configureDocs
import com.qlink.plugin.configureHttp
import com.qlink.plugin.configureKoin
import com.qlink.plugin.configureMonitoring
import com.qlink.plugin.configureRequestValidation
import com.qlink.plugin.configureResources
import com.qlink.plugin.configureRouting
import com.qlink.plugin.configureSecurity
import com.qlink.plugin.configureSerialization
import com.qlink.plugin.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import org.flywaydb.core.Flyway
import org.koin.ktor.ext.inject

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureKoin()

    val flyway by inject<Flyway>()

    flyway.migrate()

    configureHttp()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureResources()
    configureDocs()
    configureStatusPages()
    configureRequestValidation()
    configureRouting()

    val aiSummaryWorker by inject<AiSummaryWorker>()
    aiSummaryWorker.start()

    val importThumbnailWorker by inject<ImportThumbnailWorker>()
    importThumbnailWorker.start()

    val linkContentWorker by inject<LinkContentWorker>()
    linkContentWorker.start()

    val linkPreviewFillWorker by inject<LinkPreviewFillWorker>()
    linkPreviewFillWorker.start()

    val taskScheduler by inject<TaskScheduler>()
    taskScheduler.start()
    monitor.subscribe(ApplicationStopping) {
        taskScheduler.stop()
        linkPreviewFillWorker.stop()
        linkContentWorker.stop()
        importThumbnailWorker.stop()
        aiSummaryWorker.stop()
    }
}
