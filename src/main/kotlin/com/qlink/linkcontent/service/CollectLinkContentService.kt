package com.qlink.linkcontent.service

import com.qlink.common.text.hostOrNull
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.client.LinkContentExtraction
import com.qlink.linkcontent.client.LinkContentResult
import com.qlink.linkcontent.client.LinkContentSyncClient
import com.qlink.linkcontent.domain.CollectAction
import com.qlink.linkcontent.domain.CollectOutcome
import com.qlink.linkcontent.domain.CollectedPage
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.domain.service.LinkContentCollectDomainService
import com.qlink.linkcontent.dto.CollectResult
import com.qlink.linkcontent.dto.CollectStart
import com.qlink.linkcontent.repository.LinkContentRepository
import java.util.UUID
import kotlin.time.Instant

private const val LAMBDA_DEADLINE_MS = 20_000L

private val SKIPPED = CollectResult(status = null, outcome = CollectOutcome.SKIPPED)

class CollectLinkContentService(
    private val tx: TransactionRunner,
    private val linkContentRepository: LinkContentRepository,
    private val linkRepository: LinkRepository,
    private val contentClient: LinkContentSyncClient,
) {
    suspend fun collect(
        contentId: Long,
        now: Instant,
    ): CollectResult {
        val content =
            when (val start = tx.required { prepare(contentId = contentId, now = now) }) {
                null -> return SKIPPED
                is CollectStart.Done -> return start.result
                is CollectStart.Collect -> start.content
            }

        val extraction =
            contentClient.extract(
                url = content.sourceUrl,
                traceId = "lc-$contentId-${UUID.randomUUID()}",
                deadlineMs = LAMBDA_DEADLINE_MS,
            )
        val next = content.applyExtraction(extraction = extraction, now = now)
        if (!tx.required { linkContentRepository.updateCollectResult(next) }) {
            return SKIPPED.copy(host = extraction.rateLimitedHost(content), cooldownSeconds = extraction.cooldownSeconds())
        }

        return CollectResult(
            status = next.status,
            outcome = outcomeOf(extraction = extraction, next = next),
            host = extraction.rateLimitedHost(content),
            cooldownSeconds = extraction.cooldownSeconds(),
        )
    }

    private suspend fun prepare(
        contentId: Long,
        now: Instant,
    ): CollectStart? {
        val claimed = linkContentRepository.claim(contentId = contentId, staleAfter = LinkContent.STALE_AFTER, now = now) ?: return null
        val link = linkRepository.findById(claimed.linkId)
        val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = link, now = now)

        return when (preparation.action) {
            CollectAction.COLLECT -> {
                CollectStart.Collect(content = claimed)
            }

            CollectAction.LINK_GONE -> {
                CollectStart.Done(CollectResult(status = null, outcome = CollectOutcome.LINK_GONE))
            }

            CollectAction.GIVE_UP -> {
                CollectStart.Done(save(next = preparation.next, outcome = CollectOutcome.GIVE_UP))
            }

            CollectAction.URL_CHANGED -> {
                CollectStart.Done(save(next = preparation.next, outcome = CollectOutcome.URL_CHANGED))
            }
        }
    }

    private suspend fun save(
        next: LinkContent?,
        outcome: CollectOutcome,
    ): CollectResult =
        if (next != null && linkContentRepository.updateCollectResult(next)) {
            CollectResult(status = next.status, outcome = outcome)
        } else {
            SKIPPED
        }

    private fun LinkContent.applyExtraction(
        extraction: LinkContentExtraction,
        now: Instant,
    ): LinkContent =
        when (extraction) {
            is LinkContentExtraction.Collected -> {
                extraction.result.toPageOrNull()?.let { complete(page = it, now = now) }
                    ?: fail(code = ContentFailureCode.CONTENT_EMPTY, retryAfterSeconds = null, now = now)
            }

            is LinkContentExtraction.Failed -> {
                fail(code = extraction.code, retryAfterSeconds = extraction.retryAfterSeconds, now = now)
            }

            is LinkContentExtraction.Blocked -> {
                fail(code = extraction.code, retryAfterSeconds = null, now = now)
            }

            LinkContentExtraction.Throttled,
            LinkContentExtraction.Invalid,
            -> {
                release(now)
            }
        }

    private fun LinkContentResult.toPageOrNull(): CollectedPage? =
        CollectedPage.ofOrNull(
            text = text,
            title = title,
            siteName = siteName,
            author = author,
            publishedAt = publishedAt,
            canonicalUrl = canonicalUrl,
            finalUrl = finalUrl,
            language = language,
            isTruncated = isTruncated,
        )

    private fun outcomeOf(
        extraction: LinkContentExtraction,
        next: LinkContent,
    ): CollectOutcome =
        when (extraction) {
            LinkContentExtraction.Throttled -> {
                CollectOutcome.RELEASED_THROTTLED
            }

            LinkContentExtraction.Invalid -> {
                CollectOutcome.RELEASED_ERROR
            }

            else -> {
                when (next.status) {
                    ContentStatus.COLLECTED -> CollectOutcome.COLLECTED
                    ContentStatus.BLOCKED -> CollectOutcome.BLOCKED
                    ContentStatus.FAILED -> CollectOutcome.FAILED
                    ContentStatus.PENDING -> CollectOutcome.RETRY_SCHEDULED
                }
            }
        }

    private fun LinkContentExtraction.isRateLimited(): Boolean = this is LinkContentExtraction.Failed && code.isRateLimited

    private fun LinkContentExtraction.rateLimitedHost(content: LinkContent): String? =
        if (isRateLimited()) content.sourceUrl.hostOrNull() ?: content.sourceUrl else null

    private fun LinkContentExtraction.cooldownSeconds(): Int? =
        (this as? LinkContentExtraction.Failed)
            ?.takeIf { isRateLimited() }
            ?.retryAfterSeconds
            ?.let(LinkContent::clampRetryAfter)
}
