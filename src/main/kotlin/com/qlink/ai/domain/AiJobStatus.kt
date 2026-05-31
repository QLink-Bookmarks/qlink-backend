package com.qlink.ai.domain

enum class AiJobStatus(
    val description: String,
) {
    P("Pending"),
    C("Completed"),
    F("Failed"),
}
