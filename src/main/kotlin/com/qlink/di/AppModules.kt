package com.qlink.di

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
    notificationModule(log),
    aiModule(config = config, log = log),
    serviceModule(),
)
