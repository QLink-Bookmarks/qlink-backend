package com.qlink.support

import com.qlink.ai.client.AiClient
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.client.AiSummaryClientResponse
import com.qlink.ai.client.AiSummaryTodo
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import kotlinx.coroutines.channels.Channel
import org.koin.dsl.module

fun aiTestModule() =
    module {
        single {
            FakeAiClient()
        }

        single<AiClientRouter> {
            AiClientRouter(
                clients = listOf(get<FakeAiClient>()),
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

class FakeAiClient : AiClient {
    override val providerType: AiProviderType = AiProviderType.OPENAI

    override suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse =
        AiSummaryClientResponse(
            rawResponse = """{"id":1,"title":"AI 제목","summary":"AI 요약","todos":[{"title":"AI 할 일","reminderAt":null}]}""",
            title = "AI 제목",
            summary = "AI 요약",
            todos = listOf(AiSummaryTodo(title = "AI 할 일", reminderAt = null)),
            usedTokens = 11,
        )
}

suspend fun insertAiContext(
    userId: Long,
    aiProviderRepository: AiProviderRepository,
    availableModelRepository: AvailableModelRepository,
    userProviderRepository: UserProviderRepository,
    role: UserProviderRole = UserProviderRole.NORMAL,
): Pair<UserProvider, AvailableModel> {
    val provider =
        aiProviderRepository.insert(
            AiProvider(
                type = AiProviderType.OPENAI,
                baseUrl = "https://example.com",
            ),
        )
    val model =
        availableModelRepository.insert(
            AvailableModel(
                providerId = provider.id!!,
                model = "test-model-${provider.id}",
                priority = 1,
                rpdLimit = 20,
                tpdLimit = 2_000_000,
            ),
        )
    val userProvider =
        userProviderRepository.insert(
            UserProvider(
                userId = userId,
                providerId = provider.id,
                userRole = role,
                apiKey = "api-key",
            ),
        )

    return userProvider to model
}
