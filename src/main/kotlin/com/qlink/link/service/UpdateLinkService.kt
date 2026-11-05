package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.service.FolderAccessValidator
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.dto.UpdateLinkRequest
import com.qlink.link.dto.UpdateLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.service.LinkContentPlanDomainService
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.worker.LinkContentSignal
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class UpdateLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val folderAccessValidator: FolderAccessValidator,
    private val urlResolver: PreviewImageUrlResolver,
    private val linkContentRepository: LinkContentRepository,
    private val linkContentSignal: LinkContentSignal,
) {
    suspend fun updateLink(
        loginId: Long,
        linkId: Long,
        request: UpdateLinkRequest,
    ): UpdateLinkResponse {
        val result =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

                val link = linkRepository.findByIdForUpdate(linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
                link.validateOwner(loginId)

                request.folderId?.let { folderAccessValidator.validateWritable(it, loginId) }

                val updatedLink =
                    link.update(
                        folderId = request.folderId,
                        url = request.url,
                        title = request.title,
                        summary = request.summary,
                        memo = request.memo,
                        tags = request.tags,
                        thumbnailUrl = request.thumbnailUrl,
                        sourceType = request.sourceType,
                        faviconUrl = request.faviconUrl,
                        isVideo = request.isVideo,
                        previewPublicBaseUrl = urlResolver.publicBaseUrl,
                        previewEnvironment = urlResolver.environment,
                    )

                val savedLink = linkRepository.update(updatedLink)
                val resetContent =
                    LinkContentPlanDomainService
                        .forUrlChange(
                            current = linkContentRepository.findByLinkId(linkId),
                            savedLink = savedLink,
                            now = Clock.System.now(),
                        )?.let { linkContentRepository.upsertForLink(it) }

                UpdateLinkResult(
                    response = savedLink.toResponse(),
                    isContentReset = resetContent != null,
                )
            }

        if (result.isContentReset) {
            linkContentSignal.wake()
        }

        return result.response
    }

    private data class UpdateLinkResult(
        val response: UpdateLinkResponse,
        val isContentReset: Boolean,
    )

    private fun Link.toResponse(): UpdateLinkResponse =
        UpdateLinkResponse(
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            sourceType = sourceType,
        )
}
