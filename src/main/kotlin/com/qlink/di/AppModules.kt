package com.qlink.di

import com.qlink.config.NotificationConfig
import com.qlink.config.S3Config
import io.ktor.server.config.ApplicationConfig

fun appModules(config: ApplicationConfig) =
    listOf(
        dataModule(config),
        transactionModule(),
        pluginModule(config),
        repositoryModule(),
        notificationModule(NotificationConfig.from(config)),
        aiModule(config),
        storageModule(config = S3Config.from(config)),
        linkPreviewModule(config),
        bookmarkImportModule(),
        linkContentModule(config),
        serviceModule(),
    )
