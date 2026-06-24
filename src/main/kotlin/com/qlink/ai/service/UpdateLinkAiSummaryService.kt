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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
                val fixedFolder = request.folderId?.let { findOwnedFolder(loginId, it) }
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
                                        folderId = fixedFolder?.id ?: it.folderId,
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
                val titlePrompt =
                    request.title
                        ?.let { FixedTitlePrompt(it) }
                        ?: AutoTitlePrompt
                val folderPrompt =
                    fixedFolder
                        ?.let { FixedFolderPrompt(it.id!!) }
                        ?: AutoFolderPrompt(findPromptFoldersJson(loginId))

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
                            ),
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
        val folder = request.folderId?.let { findOwnedFolder(loginId, it) }

        return linkRepository.insert(
            Link(
                ownerId = loginId,
                folderId = folder?.id,
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

    private suspend fun findOwnedFolder(
        ownerId: Long,
        folderId: Long,
    ) = folderRepository.findById(folderId)?.also { it.validateOwner(ownerId) }
        ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)

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
): String =
    """
    ## Instruction
    - Understand the contents in the URL given below by the user as a bookmark.
    - Paraphrase, summarize the contents in one line. Mention the deadline/state if included in the content.
    - If a fixed title is given, return it as `title` unchanged. Otherwise, entitle the link to highlight what the user wants to know.
    - If the URL contents include any task with a deadline after today, add reasonable tasks which the user should do, including the final task for the deadline.
    - If fixed folder id is given, return the fixed folder id in the response as `folderId`.
    - If fixed folder id is not given and folders are given, choose the most suitable folder id from the folders. If no folder seems to be suitable, set null in the `folderId` in the response

    ## Link ID
    - $linkId

    ## URL
    - $requestedUrl

    ${titlePrompt.toPromptSection()}
    ${folderPrompt.toPromptSection()}
    """.trimIndent()

private sealed interface TitlePrompt {
    fun toPromptSection(): String
}

private data class FixedTitlePrompt(
    val title: String,
) : TitlePrompt {
    override fun toPromptSection(): String =
        """
        ## Fixed Title
        - $title
        """.trimIndent()
}

private data object AutoTitlePrompt : TitlePrompt {
    override fun toPromptSection(): String = ""
}

private sealed interface FolderPrompt {
    fun toPromptSection(): String
}

private data class FixedFolderPrompt(
    val folderId: Long,
) : FolderPrompt {
    override fun toPromptSection(): String =
        """
        ## Fixed Folder ID
        - $folderId
        """.trimIndent()
}

private data class AutoFolderPrompt(
    val foldersJson: String,
) : FolderPrompt {
    override fun toPromptSection(): String =
        """
        ## Folders
        $foldersJson
        """.trimIndent()
}
