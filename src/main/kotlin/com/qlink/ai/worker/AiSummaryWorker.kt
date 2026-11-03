package com.qlink.ai.worker

import com.qlink.ai.client.AiClientRouter
import com.qlink.ai.client.AiSummaryClientRequest
import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobRecoveryAction
import com.qlink.ai.domain.AiJobStatus
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
import com.qlink.config.WorkerConfig
import com.qlink.folder.repository.FolderRepository
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock

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
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
    private val aiClientRouter: AiClientRouter,
    private val apiKeyCipher: ApiKeyCipher,
    private val channel: Channel<Long>,
    private val urlResolver: PreviewImageUrlResolver,
    private val config: WorkerConfig,
    private val meterRegistry: MeterRegistry,
    private val log: Logger,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(config.aiSummaryParallelism, DISPATCHER_NAME)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val inFlight = requireNotNull(meterRegistry.gauge(IN_FLIGHT_METRIC, AtomicInteger(0)))
    private val pending = requireNotNull(meterRegistry.gauge(PENDING_METRIC, AtomicLong(0)))

    fun start() {
        scope.launch(CoroutineName("$DISPATCHER_NAME-consumer")) {
            for (jobId in channel) {
                log.info("[WORKER] Job has been detected: $jobId")
                inFlight.incrementAndGet()
                try {
                    catchingFailure { proceed(jobId) }
                        .onFailure { error ->
                            log.warn("[WORKER] Failed to process AI summary. jobId=$jobId", error)
                            catchingFailure { fail(jobId = jobId, reason = error.failureSummary()) }
                                .onFailure { log.warn("[WORKER] Failed to mark AI summary failed. jobId=$jobId", it) }
                        }
                } finally {
                    inFlight.decrementAndGet()
                }
                catchingFailure { refreshPending() }
            }
        }
        scope.launch(CoroutineName("$DISPATCHER_NAME-recovery")) {
            while (isActive) {
                catchingFailure { recover() }
                    .onFailure { log.warn("[WORKER] Failed to recover AI summary jobs", it) }
                delay(config.pollInterval)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    suspend fun recover() {
        val jobsByAction =
            tx.readOnly {
                val now = aiJobRepository.now()
                aiJobRepository.findAllByStatus(AiJobStatus.P).groupBy { it.recoveryAction(now) }
            }

        jobsByAction[AiJobRecoveryAction.FAIL].orEmpty().forEach { job ->
            markFailed(jobId = job.id!!, reason = DEFAULT_FAILURE_MESSAGE)
            log.info("[WORKER] Stale job has been failed. jobId=${job.id}")
            countResult(RESULT_EXPIRED)
        }
        jobsByAction[AiJobRecoveryAction.RUN].orEmpty().forEach { job -> channel.send(job.id!!) }
        refreshPending()
    }

    suspend fun proceed(jobId: Long) {
        val isStarted = tx.required { aiJobRepository.markStarted(aiJobId = jobId, staleAfter = AiJob.STALE_AFTER) }
        if (!isStarted) {
            log.info("[WORKER] Job is not runnable. jobId=$jobId")
            countResult(RESULT_SKIPPED)
            return
        }

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
            } ?: return countResult(RESULT_ABORTED)

        log.info(
            "[WORKER] Job Context has been loaded as jobId=$jobId, userProvider=${context.userProvider.id}, provider=${context.provider.type}, requestModel=${context.requestModel.model}",
        )

        catchingFailure { summarize(context) }
            .onSuccess { result ->
                val createdTodos =
                    tx.required {
                        val latestJob = aiJobRepository.findById(context.job.id!!) ?: return@required emptyList()
                        val latestLink = linkRepository.findById(latestJob.linkId) ?: return@required emptyList()
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
                                    previewPublicBaseUrl = urlResolver.publicBaseUrl,
                                    previewEnvironment = urlResolver.environment,
                                ).copyAiStatus(status = LinkStatus.A, workModelId = result.model.id),
                        )
                        result.response.todos.map {
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

                catchingFailure { createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) } }
                    .onSuccess { countResult(RESULT_COMPLETED) }
                    .onFailure {
                        log.warn("[WORKER] Todo notification scheduling failed after completion. jobId=$jobId", it)
                        countResult(RESULT_ERROR)
                    }
            }.onFailure {
                log.warn("AI summary failed. jobId={}", context.job.id, it)
                fail(jobId = context.job.id!!, reason = it.failureSummary())
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
                    catchingFailure {
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

    private suspend fun fail(
        jobId: Long,
        reason: String,
    ) {
        markFailed(jobId = jobId, reason = reason)
        countResult(RESULT_FAILED)
    }

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
                        previewPublicBaseUrl = urlResolver.publicBaseUrl,
                        previewEnvironment = urlResolver.environment,
                    ).copyAiStatus(status = LinkStatus.F, workModelId = link.workModelId),
            )
        }
    }

    private suspend fun refreshPending() {
        pending.set(tx.readOnly { aiJobRepository.countNotStarted() })
    }

    private fun countResult(result: String) {
        Counter
            .builder(RESULT_METRIC)
            .tag("result", result)
            .register(meterRegistry)
            .increment()
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
        const val DISPATCHER_NAME = "ai-summary-worker"
        const val IN_FLIGHT_METRIC = "ai_summary_in_flight"
        const val PENDING_METRIC = "ai_summary_pending"
        const val RESULT_METRIC = "ai_summary_results"
        const val RESULT_COMPLETED = "completed"
        const val RESULT_FAILED = "failed"
        const val RESULT_EXPIRED = "expired"
        const val RESULT_SKIPPED = "skipped"
        const val RESULT_ABORTED = "aborted"
        const val RESULT_ERROR = "error"
    }
}

private const val DEFAULT_FAILURE_MESSAGE = "AI summary generation failed"

private suspend fun <T> catchingFailure(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()
        Result.failure(error)
    } catch (error: Throwable) {
        Result.failure(error)
    }

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
