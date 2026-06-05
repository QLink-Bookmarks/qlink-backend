package com.qlink.di

import com.qlink.config.NotificationConfig
import io.ktor.server.config.ApplicationConfig
import org.slf4j.Logger

fun appModules(
    config: ApplicationConfig,
    log: Logger,
) = listOf(
    dataModule(config),
    transactionModule(),
    pluginModule(config),
    repositoryModule(),
    notificationModule(config = NotificationConfig.from(config), log = log),
    aiModule(config = config, log = log),
    serviceModule(),
)
