package com.qlink.di

import com.qlink.ai.client.AiClientConfig
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiClientRouterConfig
import com.qlink.ai.client.GeminiAiClient
import com.qlink.ai.client.OpenAiClient
import com.qlink.ai.domain.AiProviderType
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
                defaultProvider = AiProviderType.valueOf(config.string("ai.defaultProvider")),
            )
        }

        single {
            GeminiAiClient(
                httpClient = get(),
            )
        }

        single {
            OpenAiClient(
                httpClient = get(),
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
            Channel<Long>(capacity = Channel.BUFFERED)
        }

        single {
            AiSummaryDispatcher(channel = get())
        }

        single {
            AiSummaryWorker(
                tx = get(),
                aiJobRepository = get(),
                userProviderRepository = get(),
                availableModelRepository = get(),
                aiProviderRepository = get(),
                dailyUsageRepository = get(),
                linkRepository = get(),
                todoRepository = get(),
                aiClientRouter = get(),
                channel = get(),
            )
        }
    }
