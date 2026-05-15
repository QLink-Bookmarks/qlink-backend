package com.qlink.di

import io.ktor.server.config.ApplicationConfig

fun appModules(config: ApplicationConfig) =
    listOf(
        dataModule(config),
        transactionModule(),
        pluginModule(config),
    )
