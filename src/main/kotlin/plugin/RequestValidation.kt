package com.qlink.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun Application.configureRequestValidation() {
  install(RequestValidation) {
    validate<String> { bodyText ->
      if (!bodyText.startsWith("Hello")) {
        ValidationResult.Invalid("Body text should start with 'Hello'")
      } else {
        ValidationResult.Valid
      }
    }
  }
}
