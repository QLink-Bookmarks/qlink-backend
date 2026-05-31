package com.qlink.ai.worker

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.service.copyAiStatus
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
                        markFailed(jobId)
                    }
            }
        }
    }

    suspend fun proceed(jobId: Long) {
        val context =
            tx.readOnly {
                val job = aiJobRepository.findById(jobId) ?: return@readOnly null
                val userProvider = userProviderRepository.findById(job.userProviderId) ?: return@readOnly null
                val requestModel = availableModelRepository.findById(job.requestModelId) ?: return@readOnly null
                val provider = aiProviderRepository.findById(userProvider.providerId) ?: return@readOnly null
                val link = linkRepository.findById(job.linkId) ?: return@readOnly null

                AiSummaryContext(
                    job = job,
                    userProvider = userProvider,
                    provider = provider,
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
                    linkRepository.update(
                        latestLink
                            .update(
                                folderId = context.fixedFolderId ?: result.response.folderId,
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
                markFailed(context.job.id!!)
            }
    }

    private suspend fun summarize(context: AiSummaryContext): AiSummaryResult {
        val models =
            if (context.userProvider.userRole == UserProviderRole.SUPER_ADMIN) {
                context.candidateModels
            } else {
                listOf(context.requestModel)
            }
        val availableModels =
            tx.readOnly {
                models.filterNot { model ->
                    todayUsage(context.userProvider.id!!, model.id!!)?.isOverLimit(model) == true
                }
            }

        repeat(MAX_MODEL_CYCLES) { cycle ->
            availableModels.forEach { model ->
                log.info("[WORKER] Trial model=${model.model}")

                val result =
                    runCatching {
                        aiClientRouter.summarize(
                            AiSummaryClientRequest(
                                providerType = context.provider.type,
                                baseUrl = context.provider.baseUrl,
                                apiKey = context.userProvider.apiKey,
                                model = model.model,
                                prompt = context.job.prompt,
                            ),
                        )
                    }.getOrNull()

                if (result != null) {
                    check(result.linkId == null || result.linkId == context.job.linkId) {
                        "AI summary response linkId does not match. expected=${context.job.linkId}, actual=${result.linkId}"
                    }
                    check(result.folderId == null || result.folderId in context.selectableFolderIds) {
                        "AI summary response folderId is not selectable. folderId=${result.folderId}"
                    }
                    return AiSummaryResult(model = model, response = result)
                }
            }

            if (context.userProvider.userRole != UserProviderRole.SUPER_ADMIN || cycle == MAX_MODEL_CYCLES - 1) {
                throw IllegalStateException("AI summary generation failed")
            }
        }

        throw IllegalStateException("AI summary generation failed")
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

    private suspend fun markFailed(jobId: Long) {
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
                        title = "AI 요약 생성 실패",
                        summary = link.summary,
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

private fun String.extractFixedFolderId(): Long? =
    lineSequence()
        .dropWhile { it.trim() != "## Fixed Folder ID" }
        .drop(1)
        .firstOrNull { it.trim().startsWith("- ") }
        ?.trim()
        ?.removePrefix("- ")
        ?.toLongOrNull()
