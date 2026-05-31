package com.qlink.link.domain

enum class LinkStatus(
    val description: String,
) {
    G("Generating"),
    A("AI Generated"),
    C("Created"),
    F("Failed"),
}
