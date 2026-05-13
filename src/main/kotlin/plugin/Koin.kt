package com.qlink.plugin

import com.qlink.di.appModules
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
  install(Koin) {
    slf4jLogger()
    modules(appModules(environment.config))
  }
}
