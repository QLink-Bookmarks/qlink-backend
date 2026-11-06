package com.qlink.ai.service

import com.qlink.ai.repository.AiJobRepository
import com.qlink.common.transaction.TransactionRunner
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import java.net.URI
import kotlin.time.Instant

class FailAiSummaryService(
    private val tx: TransactionRunner,
    private val aiJobRepository: AiJobRepository,
    private val linkRepository: LinkRepository,
    private val urlResolver: PreviewImageUrlResolver,
) {
    suspend fun fail(
        jobId: Long,
        reason: String,
        now: Instant,
    ) {
        tx.required {
            val job = aiJobRepository.findById(jobId) ?: return@required
            val link = linkRepository.findByIdForUpdate(job.linkId) ?: return@required

            val failedLink =
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
                    ).copyAiStatus(status = LinkStatus.F, workModelId = link.workModelId)

            aiJobRepository.update(job.fail(now))
            linkRepository.updateAiFailure(
                linkId = link.id!!,
                title = failedLink.title,
                summary = failedLink.summary,
                status = failedLink.status,
            )
        }
    }

    private fun failedTitle(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

        return "AI 생성 실패 - $host"
    }
}
