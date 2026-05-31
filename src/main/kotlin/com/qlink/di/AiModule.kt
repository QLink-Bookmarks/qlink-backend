package com.qlink.di

import com.qlink.ai.client.AiClientConfig
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiClientRouterConfig
import com.qlink.ai.client.AiProvider
import com.qlink.ai.client.GeminiAiClient
import com.qlink.ai.client.OpenAiClient
import com.qlink.ai.worker.AiSummaryCommand
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.config.optionalString
import com.qlink.config.string
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun aiModule(config: ApplicationConfig) =
    module {
        single {
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        },
                    )
                }
            }
        }

        single {
            AiClientRouterConfig(
                defaultProvider = AiProvider.valueOf(config.string("ai.defaultProvider")),
            )
        }

        single {
            GeminiAiClient(
                httpClient = get(),
                config =
                    AiClientConfig(
                        provider = AiProvider.GEMINI,
                        apiKey = config.optionalString("ai.gemini.apiKey").takeIfConfigured(),
                        model = config.string("ai.gemini.model"),
                        baseUrl = config.string("ai.gemini.baseUrl"),
                    ),
            )
        }

        single {
            OpenAiClient(
                httpClient = get(),
                config =
                    AiClientConfig(
                        provider = AiProvider.OPENAI,
                        apiKey = config.optionalString("ai.openai.apiKey").takeIfConfigured(),
                        model = config.string("ai.openai.model"),
                        baseUrl = config.string("ai.openai.baseUrl"),
                    ),
            )
        }

        single {
            AiClientRouter(
                config = get(),
                clients =
                    listOf(
                        get<GeminiAiClient>(),
                        get<OpenAiClient>(),
                    ),
            )
        }

        single {
            Channel<AiSummaryCommand>(capacity = Channel.BUFFERED)
        }

        single {
            AiSummaryDispatcher(channel = get())
        }

        single {
            AiSummaryWorker(
                tx = get(),
                linkRepository = get(),
                aiClientRouter = get(),
                channel = get(),
            )
        }
    }

private fun String?.takeIfConfigured(): String? =
    this
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.startsWith("\$") && it.endsWith(":") }
