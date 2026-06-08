package com.qlink.di

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.GeminiAiClient
import com.qlink.ai.client.OpenAiClient
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.auth.client.AuthResourceClientRouter
import com.qlink.auth.client.KakaoAuthResourceClient
import com.qlink.common.crypto.ApiKeyCipher
import com.qlink.config.MonitoringConfig
import com.qlink.config.string
import com.qlink.plugin.ExternalHttpClientLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.Logger

fun aiModule(
    config: ApplicationConfig,
    log: Logger,
) = module {
    single {
        val monitoringConfig = get<MonitoringConfig>()

        HttpClient(CIO) {
            expectSuccess = false
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
        val keyBase64 =
            System
                .getenv("AI_API_KEY_ENCRYPTION_KEY_BASE64")
                ?.takeUnless { it.isBlank() }
                ?: config.string("security.aiApiKeyEncryptionKeyBase64")

        ApiKeyCipher(keyBase64 = keyBase64)
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
        KakaoAuthResourceClient(
            httpClient = get(),
        )
    }

    single {
        AuthResourceClientRouter(
            clients = listOf(get<KakaoAuthResourceClient>()),
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
            scheduleTodoNotificationService = get(),
            aiClientRouter = get(),
            apiKeyCipher = get(),
            channel = get(),
            log = log,
        )
    }
}
