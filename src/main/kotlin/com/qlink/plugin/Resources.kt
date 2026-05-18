package com.qlink.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources

fun Application.configureResources() {
    install(Resources)
}
