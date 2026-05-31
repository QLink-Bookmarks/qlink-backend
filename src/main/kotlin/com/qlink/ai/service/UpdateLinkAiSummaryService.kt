package com.qlink.ai.service

import com.qlink.ai.domain.AiJob
import com.qlink.ai.dto.AiSummaryRequest
import com.qlink.ai.dto.AiSummaryResponse
import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.ai.worker.AiSummaryDispatcher
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SourceType
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository
import java.net.URI

class UpdateLinkAiSummaryService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val userProviderRepository: UserProviderRepository,
    private val availableModelRepository: AvailableModelRepository,
    private val aiJobRepository: AiJobRepository,
    private val dispatcher: AiSummaryDispatcher,
) {
    suspend fun updateLinkAiSummary(
        loginId: Long,
        request: AiSummaryRequest,
    ): AiSummaryResponse {
        val createdJob =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

                val userProvider =
                    userProviderRepository
                        .findById(request.userProviderId)
                        ?.takeIf { it.userId == loginId }
                        ?: throw BusinessException(ErrorCode.AI_USER_PROVIDER_NOT_FOUND)
                val requestModel =
                    availableModelRepository
                        .findById(request.modelId)
                        ?.takeIf { it.providerId == userProvider.providerId }
                        ?: throw BusinessException(ErrorCode.AI_MODEL_NOT_FOUND)
                val link =
                    request.id
                        ?.let { linkId ->
                            linkRepository
                                .findById(linkId)
                                ?.also { it.validateOwner(loginId) }
                                ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
                        }?.let {
                            linkRepository.update(
                                it
                                    .update(
                                        folderId = it.folderId,
                                        url = request.url,
                                        title = request.title ?: it.title,
                                        summary = it.summary,
                                        memo = it.memo,
                                        tags = it.tags,
                                        thumbnailUrl = it.thumbnailUrl,
                                        sourceType = it.sourceType,
                                    ).copyAiStatus(status = LinkStatus.G, workModelId = requestModel.id),
                            )
                        } ?: createAiPendingLink(loginId, request)

                aiJobRepository.insert(
                    AiJob(
                        linkId = link.id!!,
                        userProviderId = userProvider.id!!,
                        requestModelId = requestModel.id!!,
                        requestedUrl = request.url,
                        prompt = createPrompt(linkId = link.id, requestedUrl = request.url),
                    ),
                )
            }

        dispatcher.dispatch(createdJob.id!!)

        return AiSummaryResponse(id = createdJob.linkId)
    }

    private suspend fun createAiPendingLink(
        loginId: Long,
        request: AiSummaryRequest,
    ): Link {
        request.folderId?.let {
            folderRepository.findById(it)?.also { folder -> folder.validateOwner(loginId) }
                ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)
        }

        return linkRepository.insert(
            Link(
                ownerId = loginId,
                folderId = request.folderId,
                url = request.url,
                title = request.title ?: pendingTitle(request.url),
                tags = emptyList(),
                sourceType = SourceType.INPUT,
                status = LinkStatus.G,
            ),
        )
    }

    private fun pendingTitle(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

        return "AI 생성 대기 중 - $host"
    }
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
        sourceType = sourceType,
        status = status,
        workModelId = workModelId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun createPrompt(
    linkId: Long,
    requestedUrl: String,
): String =
    """
    ## Instruction
    - Understand the contents in the URL given below by the user as a bookmark.
    - Paraphrase, summarize the contents in one line and entitle the link to highlight what the user wants to know. Mention the deadline/state if included in the content.
    - If the URL contents include any task with a deadline after today, add reasonable tasks which the user should do, including the final task for the deadline.

    ## Link ID
    - $linkId

    ## URL
    - $requestedUrl
    """.trimIndent()
