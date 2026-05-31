package com.qlink.ai.client

import kotlinx.serialization.Serializable

@Serializable
enum class AiProvider {
    GEMINI,
    OPENAI,
    CLAUDE,
}
