package com.qlink.di

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.GeminiAiClient
import com.qlink.ai.client.OpenAiClient
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.config.MonitoringConfig
import com.qlink.plugin.ExternalHttpClientLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.Logger

fun aiModule(log: Logger) =
    module {
        single {
            val monitoringConfig = get<MonitoringConfig>()

            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = true
                        },
                    )
                }

                install(ExternalHttpClientLogging) {
                    httpLogging = monitoringConfig.httpLogging
                }
            }
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
                folderRepository = get(),
                linkRepository = get(),
                todoRepository = get(),
                aiClientRouter = get(),
                channel = get(),
                log = log,
            )
        }
    }
