package com.qlink.ai.worker

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.service.copyAiStatus
import com.qlink.auth.domain.Role
import com.qlink.common.crypto.ApiKeyCipher
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.toJavaInstant

class AiSummaryWorker(
    private val tx: TransactionRunner,
    private val aiJobRepository: AiJobRepository,
    private val userProviderRepository: UserProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val dailyUsageRepository: DailyUsageRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val aiClientRouter: AiClientRouter,
    private val apiKeyCipher: ApiKeyCipher,
    private val channel: Channel<Long>,
    private val log: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            for (jobId in channel) {
                log.info("[WORKER] Job has been detected: $jobId")
                runCatching { proceed(jobId) }
                    .onFailure {
                        log.warn("[WORKER] Failed to process AI summary. jobId=$jobId", it)
                        markFailed(jobId = jobId, reason = it.failureSummary())
                    }
            }
        }
    }

    suspend fun proceed(jobId: Long) {
        val context =
            tx.readOnly {
                val job =
                    aiJobRepository
                        .findById(jobId)
                        ?.also { log.info("[WORKER] Job has been loaded. jobId=$jobId") }
                        ?: run {
                            log.warn("[WORKER] Job load failed. jobId=$jobId")
                            return@readOnly null
                        }
                val userProvider =
                    userProviderRepository
                        .findById(job.userProviderId)
                        ?.also {
                            log.info(
                                "[WORKER] User provider has been loaded. jobId=$jobId, userProviderId=${job.userProviderId}",
                            )
                        }
                        ?: run {
                            log.warn("[WORKER] User provider load failed. jobId=$jobId, userProviderId=${job.userProviderId}")
                            return@readOnly null
                        }
                val requestModel =
                    availableModelRepository
                        .findById(job.requestModelId)
                        ?.also {
                            log.info(
                                "[WORKER] Request model has been loaded. jobId=$jobId, requestModelId=${job.requestModelId}",
                            )
                        }
                        ?: run {
                            log.warn("[WORKER] Request model load failed. jobId=$jobId, requestModelId=${job.requestModelId}")
                            return@readOnly null
                        }
                val provider =
                    aiProviderRepository
                        .findById(userProvider.providerId)
                        ?.also {
                            log.info(
                                "[WORKER] AI provider has been loaded. jobId=$jobId, providerId=${userProvider.providerId}",
                            )
                        }
                        ?: run {
                            log.warn("[WORKER] AI provider load failed. jobId=$jobId, providerId=${userProvider.providerId}")
                            return@readOnly null
                        }
                val link =
                    linkRepository
                        .findById(job.linkId)
                        ?.also { log.info("[WORKER] Link has been loaded. jobId=$jobId, linkId=${job.linkId}") }
                        ?: run {
                            log.warn("[WORKER] Link load failed. jobId=$jobId, linkId=${job.linkId}")
                            return@readOnly null
                        }
                val apiKey =
                    apiKeyCipher
                        .decrypt(userProvider.apiKey)
                        ?.also {
                            log.info(
                                "[WORKER] User provider API key has been decrypted. jobId=$jobId, userProviderId=${userProvider.id}",
                            )
                        }
                        ?: run {
                            log.warn("[WORKER] User provider API key decrypt failed. jobId=$jobId, userProviderId=${userProvider.id}")
                            return@readOnly null
                        }

                AiSummaryContext(
                    job = job,
                    userProvider = userProvider,
                    provider = provider,
                    apiKey = apiKey,
                    requestModel = requestModel,
                    candidateModels = availableModelRepository.findAllByProviderId(provider.id!!),
                    selectableFolderIds = folderRepository.findAllByOwnerId(link.ownerId).mapNotNull { it.id }.toSet(),
                    fixedFolderId = job.prompt.extractFixedFolderId(),
                )
            } ?: return

        log.info(
            "[WORKER] Job Context has been loaded as jobId=$jobId, userProvider=${context.userProvider.id}, provider=${context.provider.type}, requestModel=${context.requestModel.model}",
        )

        runCatching { summarize(context) }
            .onSuccess { result ->
                tx.required {
                    val latestJob = aiJobRepository.findById(context.job.id!!) ?: return@required
                    val latestLink = linkRepository.findById(latestJob.linkId) ?: return@required
                    val completedAt = Clock.System.now()

                    log.info("[WORKER] Job has been successful for job=$jobId, link=${latestLink.id}")

                    aiJobRepository.update(
                        latestJob.complete(
                            responseModelId = result.model.id!!,
                            response = result.response.rawResponse,
                            completedAt = completedAt,
                        ),
                    )
                    upsertDailyUsage(context.userProvider, result.model, result.response.usedTokens)
                    val responseFolderId =
                        result.response.folderId?.takeIf { it in context.selectableFolderIds }
                    linkRepository.update(
                        latestLink
                            .update(
                                folderId = context.fixedFolderId ?: responseFolderId,
                                url = latestLink.url,
                                title = result.response.title,
                                summary = result.response.summary,
                                memo = latestLink.memo,
                                tags = result.response.tags,
                                thumbnailUrl = latestLink.thumbnailUrl,
                                sourceType = latestLink.sourceType,
                            ).copyAiStatus(status = LinkStatus.A, workModelId = result.model.id),
                    )
                    result.response.todos.forEach {
                        todoRepository.insert(
                            Todo(
                                linkId = latestLink.id!!,
                                ownerId = latestLink.ownerId,
                                title = it.title,
                                reminderAt = it.reminderAt,
                            ),
                        )
                    }
                }
            }.onFailure {
                log.warn("AI summary failed. jobId={}", context.job.id, it)
                markFailed(jobId = context.job.id!!, reason = it.failureSummary())
            }
    }

    private suspend fun summarize(context: AiSummaryContext): AiSummaryResult {
        val models =
            if (context.userProvider.userRole == Role.SUPER_ADMIN) {
                context.candidateModels
            } else {
                listOf(context.requestModel)
            }
        var lastFailure: Throwable? = null
        val availableModels =
            tx.readOnly {
                models.filterNot { model ->
                    todayUsage(context.userProvider.id!!, model.id!!)?.isOverLimit(model) == true
                }
            }

        repeat(MAX_MODEL_CYCLES) { cycle ->
            availableModels.forEach { model ->
                log.info("[WORKER] Trial model=${model.model}")

                val summary =
                    runCatching {
                        aiClientRouter.summarize(
                            AiSummaryClientRequest(
                                providerType = context.provider.type,
                                baseUrl = context.provider.baseUrl,
                                apiKey = context.apiKey,
                                model = model.model,
                                prompt = context.job.prompt,
                            ),
                        )
                    }
                val result = summary.getOrNull()
                if (result == null) {
                    lastFailure = summary.exceptionOrNull()
                    return@forEach
                }

                check(result.linkId == null || result.linkId == context.job.linkId) {
                    "AI summary response linkId does not match. expected=${context.job.linkId}, actual=${result.linkId}"
                }
                return AiSummaryResult(model = model, response = result)
            }

            if (context.userProvider.userRole != Role.SUPER_ADMIN || cycle == MAX_MODEL_CYCLES - 1) {
                throw IllegalStateException(lastFailure.failureSummary(), lastFailure)
            }
        }

        throw IllegalStateException(lastFailure.failureSummary(), lastFailure)
    }

    private suspend fun upsertDailyUsage(
        userProvider: UserProvider,
        model: AvailableModel,
        tokens: Int,
    ) {
        val usage = todayUsage(userProvider.id!!, model.id!!)
        val updated =
            usage?.addUsage(tokens)
                ?: DailyUsage(
                    userProviderId = userProvider.id,
                    modelId = model.id,
                    usageDate = LocalDate.now(ZoneOffset.UTC),
                    requests = 1,
                    tokens = tokens,
                )

        if (usage == null) {
            dailyUsageRepository.insert(updated)
        } else {
            dailyUsageRepository.update(updated)
        }
    }

    private suspend fun todayUsage(
        userProviderId: Long,
        modelId: Long,
    ): DailyUsage? =
        dailyUsageRepository.findByUserIdAndProviderIdAndUsageDate(
            userProviderId = userProviderId,
            modelId = modelId,
            usageDate = LocalDate.now(ZoneOffset.UTC),
        )

    private suspend fun markFailed(
        jobId: Long,
        reason: String,
    ) {
        tx.required {
            val job = aiJobRepository.findById(jobId) ?: return@required
            val link = linkRepository.findById(job.linkId) ?: return@required
            val completedAt = Clock.System.now()

            aiJobRepository.update(job.fail(completedAt))
            linkRepository.update(
                link
                    .update(
                        folderId = link.folderId,
                        url = link.url,
                        title = failedTitle(link.url),
                        summary = reason,
                        memo = link.memo,
                        tags = link.tags,
                        thumbnailUrl = link.thumbnailUrl,
                        sourceType = link.sourceType,
                    ).copyAiStatus(status = LinkStatus.F, workModelId = link.workModelId),
            )
        }
    }

    private data class AiSummaryContext(
        val job: AiJob,
        val userProvider: UserProvider,
        val provider: AiProvider,
        val apiKey: String,
        val requestModel: AvailableModel,
        val candidateModels: List<AvailableModel>,
        val selectableFolderIds: Set<Long>,
        val fixedFolderId: Long?,
    )

    private data class AiSummaryResult(
        val model: AvailableModel,
        val response: com.qlink.ai.client.AiSummaryClientResponse,
    )

    private companion object {
        const val MAX_MODEL_CYCLES = 5
    }
}

private const val DEFAULT_FAILURE_MESSAGE = "AI summary generation failed"

private fun Throwable?.failureSummary(): String =
    this
        ?.message
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_FAILURE_MESSAGE

private fun failedTitle(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    return "AI 생성 실패 - $host"
}

private fun String.extractFixedFolderId(): Long? =
    lineSequence()
        .dropWhile { it.trim() != "## Fixed Folder ID" }
        .drop(1)
        .firstOrNull { it.trim().startsWith("- ") }
        ?.trim()
        ?.removePrefix("- ")
        ?.toLongOrNull()
