package com.qlink.plugin

import com.qlink.config.HttpPluginConfig
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import org.koin.ktor.ext.inject

fun Application.configureHttp() {
    val config by inject<HttpPluginConfig>()

    install(CORS) {
        config.cors.methods.forEach { method ->
            allowMethod(HttpMethod(method))
        }
        config.cors.headers.forEach { header ->
            allowHeader(header)
        }
        if (config.cors.anyHost) {
            anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
        } else {
            config.cors.hosts.forEach { host ->
                allowHost(host)
            }
        }
    }
    install(Compression)
    install(DefaultHeaders) {
        config.defaultHeaders.forEach { (name, value) ->
            header(name, value)
        }
    }
}
