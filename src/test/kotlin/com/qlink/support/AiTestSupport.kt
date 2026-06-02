package com.qlink.support

import com.qlink.ai.client.AiApiKeyValidationException
import com.qlink.ai.client.AiApiKeyValidationRequest
import com.qlink.ai.client.AiClient
import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.client.AiSummaryClientResponse
import com.qlink.ai.client.AiSummaryTodo
import com.qlink.ai.crypto.AiApiKeyCipher
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.ai.worker.AiSummaryWorker
import com.qlink.auth.domain.Role
import kotlinx.coroutines.channels.Channel
import org.koin.dsl.module
import org.slf4j.LoggerFactory

fun aiTestModule() =
    module {
        single {
            AiApiKeyCipher(keyBase64 = TEST_AI_API_KEY_ENCRYPTION_KEY_BASE64)
        }

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
                folderRepository = get(),
                linkRepository = get(),
                todoRepository = get(),
                aiClientRouter = get(),
                apiKeyCipher = get(),
                channel = get(),
                log = LoggerFactory.getLogger(AiSummaryWorker::class.java),
            )
        }
    }

class FakeAiClient : AiClient {
    override val providerType: AiProviderType = AiProviderType.OPENAI
    var linkId: Long? = null
    var folderId: Long? = null
    var tags: List<String> = listOf("AI 태그")
    var usedTokens: Int = 11
    var failuresByModel: Map<String, Throwable> = emptyMap()
    val requestedModels: MutableList<String> = mutableListOf()
    val requestedApiKeys: MutableList<String> = mutableListOf()
    var validationStatusCode: Int? = null

    override suspend fun summarize(request: AiSummaryClientRequest): AiSummaryClientResponse {
        requestedModels.add(request.model)
        requestedApiKeys.add(request.apiKey)
        failuresByModel[request.model]?.let { throw it }

        return AiSummaryClientResponse(
            linkId = linkId,
            folderId = folderId,
            rawResponse = """{"id":1,"title":"AI 제목","summary":"AI 요약","todos":[{"title":"AI 할 일","reminderAt":null}]}""",
            title = "AI 제목",
            summary = "AI 요약",
            tags = tags,
            todos = listOf(AiSummaryTodo(title = "AI 할 일", reminderAt = null)),
            usedTokens = usedTokens,
        )
    }

    override suspend fun validateApiKey(request: AiApiKeyValidationRequest) {
        requestedApiKeys.add(request.apiKey)
        validationStatusCode?.let { throw AiApiKeyValidationException(it) }
    }

    fun reset() {
        linkId = null
        folderId = null
        tags = listOf("AI 태그")
        usedTokens = 11
        failuresByModel = emptyMap()
        requestedModels.clear()
        requestedApiKeys.clear()
        validationStatusCode = null
    }
}

suspend fun insertAiContext(
    userId: Long,
    aiProviderRepository: AiProviderRepository,
    availableModelRepository: AvailableModelRepository,
    userProviderRepository: UserProviderRepository,
    role: Role = Role.NORMAL,
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
                apiKey = AiApiKeyCipher(TEST_AI_API_KEY_ENCRYPTION_KEY_BASE64).encrypt("api-key"),
            ),
        )

    return userProvider to model
}

const val TEST_AI_API_KEY_ENCRYPTION_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
