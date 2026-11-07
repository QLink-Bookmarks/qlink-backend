package com.qlink.ai.service

import com.qlink.ai.domain.AiJob
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.dto.AiSummaryResponse
import com.qlink.ai.dto.AiSummaryTodoRequest
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.folder.service.FolderAccessValidator
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SourceType
import com.qlink.link.repository.LinkRepository
import com.qlink.link.worker.LinkPreviewFillSignal
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.domain.service.LinkContentPlanDomainService
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.worker.LinkContentSignal
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Instant

class UpdateLinkAiSummaryService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val folderAccessValidator: FolderAccessValidator,
    private val linkRepository: LinkRepository,
    private val userProviderRepository: UserProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
    private val aiJobRepository: AiJobRepository,
    private val dispatcher: AiSummaryDispatcher,
    private val urlResolver: PreviewImageUrlResolver,
    private val linkContentRepository: LinkContentRepository,
    private val linkContentSignal: LinkContentSignal,
    private val linkPreviewFillSignal: LinkPreviewFillSignal,
    private val todoRepository: TodoRepository,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun updateLinkAiSummary(
        loginId: Long,
        request: AiSummaryRequest,
    ): AiSummaryResponse {
        val result =
            tx.required {
                (request.id != null && (request.memo != null || !request.todos.isNullOrEmpty()))
                    .requireFalse(ErrorCode.LINK_AI_EXISTING_LINK_CONTENT_NOT_ALLOWED)
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

                val userProvider =
                    userProviderRepository
                        .findById(request.userProviderId)
                        ?.also { it.validateAccessibleBy(loginId) }
                        ?: throw BusinessException(ErrorCode.AI_USER_PROVIDER_NOT_FOUND)
                val requestModel =
                    availableModelRepository
                        .findById(request.modelId)
                        ?.takeIf { it.providerId == userProvider.providerId }
                        ?: throw BusinessException(ErrorCode.AI_MODEL_NOT_FOUND)
                request.folderId?.let { folderRepository.findAllByIdsForKeyShare(listOf(it)) }
                val fixedFolder = request.folderId?.let { findWritableFolder(loginId, it) }
                val now = Clock.System.now()
                val link =
                    request.id
                        ?.let { linkId ->
                            linkRepository
                                .findByIdForUpdate(linkId)
                                ?.also { it.validateOwner(loginId) }
                                ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
                        }?.let {
                            linkRepository
                                .update(
                                    it
                                        .update(
                                            folderId = fixedFolder?.id ?: it.folderId,
                                            url = request.url,
                                            title = request.title ?: it.title,
                                            summary = it.summary,
                                            memo = it.memo,
                                            tags = it.tags,
                                            thumbnailUrl = request.thumbnailUrl,
                                            sourceType = it.sourceType,
                                            faviconUrl = request.faviconUrl,
                                            isVideo = request.isVideo,
                                            previewPublicBaseUrl = urlResolver.publicBaseUrl,
                                            previewEnvironment = urlResolver.environment,
                                        ).copyAiStatus(status = LinkStatus.G, workModelId = requestModel.id),
                                ).also { resetContentForUrlChange(savedLink = it, now = now) }
                        } ?: createAiPendingLink(loginId, request).also { createContent(link = it, now = now) }
                val createdTodos =
                    request.todos.orEmpty().map { todoRequest ->
                        todoRepository.insert(
                            todoRequest.toTodo(
                                linkId = link.id!!,
                                ownerId = loginId,
                                now = now,
                            ),
                        )
                    }
                val titlePrompt =
                    request.title
                        ?.let { FixedTitlePrompt(it) }
                        ?: AutoTitlePrompt
                val folderPrompt =
                    fixedFolder
                        ?.let { FixedFolderPrompt(it.id!!) }
                        ?: AutoFolderPrompt(findPromptFoldersJson(loginId))
                val todoPrompt = if (request.generateTodo) GenerateTodoPrompt else SkipTodoPrompt

                val createdJob =
                    aiJobRepository.insert(
                        AiJob(
                            linkId = link.id!!,
                            userProviderId = userProvider.id!!,
                            requestModelId = requestModel.id!!,
                            requestedUrl = request.url,
                            prompt =
                                createPrompt(
                                    linkId = link.id,
                                    requestedUrl = request.url,
                                    titlePrompt = titlePrompt,
                                    folderPrompt = folderPrompt,
                                    todoPrompt = todoPrompt,
                                ),
                        ),
                    )

                UpdateLinkAiSummaryResult(
                    job = createdJob,
                    createdTodos = createdTodos,
                    previewFillLinkId = link.id.takeUnless { request.id != null || link.hasPreview },
                )
            }

        linkContentSignal.wake()
        result.previewFillLinkId?.let(linkPreviewFillSignal::wake)
        dispatcher.dispatch(result.job.id!!)
        result.createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) }

        return AiSummaryResponse(id = result.job.linkId)
    }

    private data class UpdateLinkAiSummaryResult(
        val job: AiJob,
        val createdTodos: List<Todo>,
        val previewFillLinkId: Long?,
    )

    private fun AiSummaryTodoRequest.toTodo(
        linkId: Long,
        ownerId: Long,
        now: Instant,
    ): Todo =
        Todo.create(
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
            now = now,
        )

    private suspend fun createContent(
        link: Link,
        now: Instant,
    ) {
        linkContentRepository.insertAll(
            listOf(
                LinkContent.create(
                    linkId = link.id!!,
                    sourceUrl = link.url,
                    priority = ContentPriority.INTERACTIVE,
                    now = now,
                ),
            ),
        )
    }

    private suspend fun resetContentForUrlChange(
        savedLink: Link,
        now: Instant,
    ) {
        LinkContentPlanDomainService
            .forUrlChange(
                current = linkContentRepository.findByLinkId(savedLink.id!!),
                savedLink = savedLink,
                now = now,
            )?.let { linkContentRepository.upsertForLink(it) }
    }

    private suspend fun createAiPendingLink(
        loginId: Long,
        request: AiSummaryRequest,
    ): Link {
        val folder = request.folderId?.let { findWritableFolder(loginId, it) }

        return linkRepository.insert(
            Link.createFromRequest(
                ownerId = loginId,
                folderId = folder?.id,
                url = request.url,
                title = request.title ?: pendingTitle(request.url),
                tags = emptyList(),
                summary = null,
                memo = request.memo,
                thumbnailUrl = request.thumbnailUrl,
                faviconUrl = request.faviconUrl,
                isVideo = request.isVideo,
                sourceType = SourceType.INPUT,
                status = LinkStatus.G,
                previewPublicBaseUrl = urlResolver.publicBaseUrl,
                previewEnvironment = urlResolver.environment,
            ),
        )
    }

    private fun pendingTitle(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

        return "AI 생성 대기 중 - $host"
    }

    private suspend fun findWritableFolder(
        ownerId: Long,
        folderId: Long,
    ) = folderAccessValidator.validateWritable(folderId, ownerId)

    private suspend fun findPromptFoldersJson(ownerId: Long): String =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("id", JsonNull)
                    put("title", "미분류")
                },
            )
            folderRepository.findAllByOwnerId(ownerId).forEach { folder ->
                add(
                    buildJsonObject {
                        put("id", folder.id)
                        put("title", folder.name)
                    },
                )
            }
        }.toString()
}

fun Link.copyAiStatus(
    status: LinkStatus,
    workModelId: Long?,
): Link =
    Link(
        id = id,
        ownerId = ownerId,
        folderId = folderId,
        url = url,
        title = title,
        summary = summary,
        memo = memo,
        tags = tags,
        thumbnailUrl = thumbnailUrl,
        faviconUrl = faviconUrl,
        isVideo = isVideo,
        sourceType = sourceType,
        status = status,
        workModelId = workModelId,
        favoriteAt = favoriteAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun createPrompt(
    linkId: Long,
    requestedUrl: String,
    titlePrompt: TitlePrompt,
    folderPrompt: FolderPrompt,
    todoPrompt: TodoPrompt,
): String =
    """
    ## Instruction
    - Understand the contents in the URL given below by the user as a bookmark.
    - Paraphrase, summarize the contents in one line. Mention the deadline/state if included in the content.
    - ${titlePrompt.toInstruction()}
    - ${todoPrompt.toInstruction()}
    - ${folderPrompt.toInstruction()}

    ## Link ID
    - $linkId

    ## URL
    - $requestedUrl

    ${titlePrompt.toPromptSection()}
    ${folderPrompt.toPromptSection()}
    """.trimIndent()

private sealed interface TitlePrompt {
    fun toInstruction(): String

    fun toPromptSection(): String
}

private data class FixedTitlePrompt(
    val title: String,
) : TitlePrompt {
    override fun toInstruction(): String = "Return the fixed title given below as `title` unchanged."

    override fun toPromptSection(): String =
        """
        ## Fixed Title
        - $title
        """.trimIndent()
}

private data object AutoTitlePrompt : TitlePrompt {
    override fun toInstruction(): String = "Entitle the link to highlight what the user wants to know."

    override fun toPromptSection(): String = ""
}

private sealed interface FolderPrompt {
    fun toInstruction(): String

    fun toPromptSection(): String
}

private data class FixedFolderPrompt(
    val folderId: Long,
) : FolderPrompt {
    override fun toInstruction(): String = "Return the fixed folder id given below as `folderId`."

    override fun toPromptSection(): String =
        """
        ## Fixed Folder ID
        - $folderId
        """.trimIndent()
}

private data class AutoFolderPrompt(
    val foldersJson: String,
) : FolderPrompt {
    override fun toInstruction(): String =
        "Choose the most suitable folder id from the folders below. If none is suitable, set `folderId` null."

    override fun toPromptSection(): String =
        """
        ## Folders
        $foldersJson
        """.trimIndent()
}

private sealed interface TodoPrompt {
    fun toInstruction(): String
}

private data object GenerateTodoPrompt : TodoPrompt {
    override fun toInstruction(): String =
        "If the URL contents include any task with a deadline after today, add reasonable tasks which the user " +
            "should do, including the final task for the deadline."
}

private data object SkipTodoPrompt : TodoPrompt {
    override fun toInstruction(): String = "Do not generate any tasks. Return an empty `todos` array."
}
