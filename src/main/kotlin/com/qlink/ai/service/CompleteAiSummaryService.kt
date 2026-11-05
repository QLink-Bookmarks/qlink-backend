package com.qlink.ai.service

import com.qlink.ai.domain.DailyUsage
import com.qlink.ai.dto.AiSummaryCompletion
import com.qlink.ai.dto.AiSummaryCompletionResult
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.service.LinkAiFolderDomainService
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock

class CompleteAiSummaryService(
    private val tx: TransactionRunner,
    private val aiJobRepository: AiJobRepository,
    private val dailyUsageRepository: DailyUsageRepository,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
    private val urlResolver: PreviewImageUrlResolver,
) {
    private val log = LoggerFactory.getLogger(CompleteAiSummaryService::class.java)

    suspend fun complete(completion: AiSummaryCompletion): AiSummaryCompletionResult {
        val createdTodos =
            tx.required {
                val snapshot = completion.snapshot
                // Discover the owner only; all decisions use the subsequently locked facts.
                val observedLink = linkRepository.findById(snapshot.linkId) ?: return@required emptyList()
                val folders =
                    folderRepository.findAllByIdsForUpdate(
                        listOfNotNull(completion.candidateFolderId),
                    )
                val candidate = folders.find { it.id == completion.candidateFolderId }
                val member =
                    candidate?.let {
                        folderMemberRepository.findByFolderIdAndUserIdForUpdate(
                            folderId = it.id!!,
                            userId = observedLink.ownerId,
                        )
                    }
                // Never acquire another folder lock after taking the link lock.
                val link = linkRepository.findByIdForUpdate(snapshot.linkId) ?: return@required emptyList()
                val job = aiJobRepository.findById(snapshot.jobId) ?: return@required emptyList()
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = snapshot.folderAssignmentRevision,
                        candidateFolderId = completion.candidateFolderId,
                        candidate = candidate,
                        member = member,
                    )
                val response = completion.response
                aiJobRepository.update(
                    job.complete(
                        responseModelId = completion.responseModelId,
                        response = response.rawResponse,
                        completedAt = Clock.System.now(),
                    ),
                )
                val usageDate = LocalDate.now(ZoneOffset.UTC)
                val usage =
                    dailyUsageRepository.findByUserIdAndProviderIdAndUsageDate(
                        userProviderId = job.userProviderId,
                        modelId = completion.responseModelId,
                        usageDate = usageDate,
                    )
                val updatedUsage =
                    usage?.addUsage(response.usedTokens)
                        ?: DailyUsage(
                            userProviderId = job.userProviderId,
                            modelId = completion.responseModelId,
                            usageDate = usageDate,
                            requests = 1,
                            tokens = response.usedTokens,
                        )
                if (usage == null) {
                    dailyUsageRepository.insert(updatedUsage)
                } else {
                    dailyUsageRepository.update(updatedUsage)
                }
                val summarizedLink =
                    link
                        .update(
                            folderId = folderId,
                            url = link.url,
                            title = response.title,
                            summary = response.summary,
                            memo = link.memo,
                            tags = response.tags,
                            thumbnailUrl = link.thumbnailUrl,
                            sourceType = link.sourceType,
                            previewPublicBaseUrl = urlResolver.publicBaseUrl,
                            previewEnvironment = urlResolver.environment,
                        ).copyAiStatus(status = LinkStatus.A, workModelId = completion.responseModelId)
                linkRepository.updateAiResult(
                    linkId = link.id!!,
                    folderId = summarizedLink.folderId,
                    title = summarizedLink.title,
                    summary = summarizedLink.summary,
                    tags = summarizedLink.tags,
                    status = summarizedLink.status,
                    workModelId = summarizedLink.workModelId,
                )
                response.todos.map {
                    todoRepository.insert(
                        Todo(
                            linkId = link.id!!,
                            ownerId = link.ownerId,
                            title = it.title,
                            reminderAt = it.reminderAt,
                        ),
                    )
                }
            }

        // A scheduling failure after commit must not turn a successful AI result into a failed job.
        return try {
            createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) }
            AiSummaryCompletionResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("[AI_SUMMARY] Todo notification scheduling failed after completion. jobId=${completion.snapshot.jobId}", error)
            AiSummaryCompletionResult(schedulingFailed = true)
        }
    }
}
