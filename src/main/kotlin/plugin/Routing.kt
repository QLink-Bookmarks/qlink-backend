package com.qlink.plugin

import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
@Resource("/articles")
private class Articles(val sort: String? = "new")

fun Application.configureRouting() {
  routing {
    get("/") {
      call.respondText("Hello, World!")
    }
    get<Articles> { article ->
      call.respond("List of articles sorted starting from ${article.sort}")
    }
    get("/json/kotlinx-serialization") {
      call.respond(mapOf("hello" to "world"))
    }
  }
}
