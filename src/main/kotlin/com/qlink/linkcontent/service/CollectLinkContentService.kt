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
import com.qlink.linkcontent.repository.LinkContentRepository
import java.util.UUID
import kotlin.time.Instant

/** Lambda 핸들러 안의 수집 deadline. Lambda 타임아웃(30초)과 서버 호출 타임아웃(45초)보다 짧다. */
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
            when (val prepared = tx.required { prepare(contentId = contentId, now = now) }) {
                null -> return SKIPPED
                is Prepared.Done -> return prepared.result
                is Prepared.Collect -> prepared.content
            }

        val extraction =
            contentClient.extract(
                url = content.sourceUrl,
                traceId = UUID.randomUUID().toString(),
                deadlineMs = LAMBDA_DEADLINE_MS,
            )
        val next = content.applyExtraction(extraction = extraction, now = now)
        if (!tx.required { linkContentRepository.updateClaimed(fence = content.fence, next = next) }) return SKIPPED

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
    ): Prepared? {
        val claimed = linkContentRepository.claim(contentId = contentId, staleAfter = LinkContent.STALE_AFTER, now = now) ?: return null
        val link = linkRepository.findById(claimed.linkId)
        val preparation = LinkContentCollectDomainService.prepare(claimed = claimed, link = link, now = now)

        return when (preparation.action) {
            CollectAction.COLLECT -> {
                Prepared.Collect(content = claimed)
            }

            CollectAction.LINK_GONE -> {
                Prepared.Done(CollectResult(status = null, outcome = CollectOutcome.LINK_GONE))
            }

            CollectAction.GIVE_UP -> {
                Prepared.Done(save(claimed = claimed, next = preparation.next, outcome = CollectOutcome.GIVE_UP))
            }

            CollectAction.URL_CHANGED -> {
                Prepared.Done(
                    save(claimed = claimed, next = preparation.next, outcome = CollectOutcome.URL_CHANGED),
                )
            }
        }
    }

    private suspend fun save(
        claimed: LinkContent,
        next: LinkContent?,
        outcome: CollectOutcome,
    ): CollectResult =
        if (next != null && linkContentRepository.updateClaimed(fence = claimed.fence, next = next)) {
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

    /** Lambda 결과를 VO로 옮긴다. 정규화 뒤 빈 본문은 VO가 거절하므로 null(→ `CONTENT_EMPTY`). */
    private fun LinkContentResult.toPageOrNull(): CollectedPage? =
        text.takeIf(String::isNotBlank)?.let {
            CollectedPage(
                text = it,
                title = title,
                siteName = siteName,
                author = author,
                publishedAt = publishedAt,
                canonicalUrl = canonicalUrl,
                finalUrl = finalUrl,
                language = language,
                isTruncated = isTruncated,
            )
        }

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

    private fun LinkContentExtraction.isRateLimited(): Boolean =
        this is LinkContentExtraction.Failed && code == ContentFailureCode.UPSTREAM_RATE_LIMITED

    /** 429를 받은 호스트. 워커가 인스턴스 메모리 쿨다운에 쓴다. */
    private fun LinkContentExtraction.rateLimitedHost(content: LinkContent): String? =
        if (isRateLimited()) content.sourceUrl.hostOrNull() ?: content.sourceUrl else null

    /** 429의 Retry-After를 0~900초로 자른 값. 없으면 null(워커 기본 쿨다운). */
    private fun LinkContentExtraction.cooldownSeconds(): Int? =
        (this as? LinkContentExtraction.Failed)
            ?.takeIf { isRateLimited() }
            ?.retryAfterSeconds
            ?.coerceIn(0, LinkContent.RETRY_AFTER_MAX.inWholeSeconds.toInt())

    private sealed interface Prepared {
        data class Collect(
            val content: LinkContent,
        ) : Prepared

        data class Done(
            val result: CollectResult,
        ) : Prepared
    }
}
