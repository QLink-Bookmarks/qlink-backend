package com.qlink.linkcontent.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requirePresent
import com.qlink.common.error.requireTrue
import com.qlink.common.text.collapseWhitespace
import com.qlink.common.text.sha256Hex
import com.qlink.common.text.takeKeepingSurrogatePairs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val ELLIPSIS = "…"

class LinkContent(
    val id: Long? = null,
    val linkId: Long,
    val sourceUrl: String,
    val status: ContentStatus,
    val priority: ContentPriority,
    val claimCount: Int = 0,
    val attempt: Int = 0,
    val startedAt: Instant? = null,
    val nextAttemptAt: Instant,
    val failureCode: ContentFailureCode? = null,
    val page: CollectedPage? = null,
    val contentHash: String? = null,
    val excerpt: String? = null,
    val collectedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val contentLength: Int?
        get() = page?.text?.length

    init {
        if (status == ContentStatus.COLLECTED) {
            validateCollected()
        } else {
            validateNotCollected()
        }
    }

    fun hasExceededClaims(): Boolean = claimCount > MAX_CLAIMS

    fun complete(
        page: CollectedPage,
        now: Instant,
    ): LinkContent =
        copy(
            status = ContentStatus.COLLECTED,
            startedAt = null,
            failureCode = null,
            page = page,
            contentHash = page.text.sha256Hex(),
            excerpt = buildExcerpt(page.text),
            collectedAt = now,
        )

    fun fail(
        code: ContentFailureCode,
        retryAfterSeconds: Int?,
        now: Instant,
    ): LinkContent =
        when {
            code.isBlocking -> {
                finish(status = ContentStatus.BLOCKED, code = code, attempt = attempt)
            }

            code.isRetryable && attempt + 1 < MAX_ATTEMPTS -> {
                copy(
                    status = ContentStatus.PENDING,
                    attempt = attempt + 1,
                    startedAt = null,
                    nextAttemptAt = now + retryDelay(retryAfterSeconds),
                    failureCode = code,
                )
            }

            code.isRetryable -> {
                finish(status = ContentStatus.FAILED, code = code, attempt = attempt + 1)
            }

            else -> {
                finish(status = ContentStatus.FAILED, code = code, attempt = attempt)
            }
        }

    fun release(now: Instant): LinkContent =
        copy(
            status = ContentStatus.PENDING,
            startedAt = null,
            nextAttemptAt = now + RELEASE_DELAY,
        )

    fun giveUp(): LinkContent = finish(status = ContentStatus.FAILED, code = ContentFailureCode.WORKER_GIVE_UP, attempt = attempt)

    fun resetForUrl(
        sourceUrl: String,
        now: Instant,
    ): LinkContent =
        LinkContent(
            id = id,
            linkId = linkId,
            sourceUrl = sourceUrl,
            status = ContentStatus.PENDING,
            priority = ContentPriority.INTERACTIVE,
            nextAttemptAt = now,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun finish(
        status: ContentStatus,
        code: ContentFailureCode,
        attempt: Int,
    ): LinkContent =
        copy(
            status = status,
            attempt = attempt,
            startedAt = null,
            failureCode = code,
        )

    private fun retryDelay(retryAfterSeconds: Int?): Duration {
        val backoff = BACKOFF[attempt.coerceAtMost(BACKOFF.lastIndex)]
        val retryAfter = clampRetryAfter(retryAfterSeconds ?: 0).seconds
        return maxOf(backoff, retryAfter)
    }

    private fun validateCollected() {
        val text = page.requirePresent(ErrorCode.LINK_CONTENT_PAGE_REQUIRED).text
        contentHash.requirePresent(ErrorCode.LINK_CONTENT_HASH_REQUIRED)
        collectedAt.requirePresent(ErrorCode.LINK_CONTENT_COLLECTED_AT_REQUIRED)

        val built = buildExcerpt(text)
        when (excerpt) {
            null -> {
                (built == null).requireTrue(ErrorCode.LINK_CONTENT_EXCERPT_REQUIRED)
            }

            else -> {
                excerpt.endsWith(ELLIPSIS).requireTrue(ErrorCode.LINK_CONTENT_EXCERPT_SUFFIX_INVALID)
                (excerpt.length <= EXCERPT_MAX_LENGTH).requireTrue(ErrorCode.LINK_CONTENT_EXCERPT_OVER_MAX)
                (excerpt.length - ELLIPSIS.length <= excerptBudget(text.collapseWhitespace().length))
                    .requireTrue(ErrorCode.LINK_CONTENT_EXCERPT_OVER_BUDGET)
                (built != null).requireTrue(ErrorCode.LINK_CONTENT_EXCERPT_NOT_ALLOWED)
            }
        }
    }

    private fun validateNotCollected() {
        (page == null && contentHash == null && excerpt == null && collectedAt == null)
            .requireTrue(ErrorCode.LINK_CONTENT_COLLECTED_FIELDS_NOT_ALLOWED)
    }

    private fun copy(
        status: ContentStatus = this.status,
        attempt: Int = this.attempt,
        startedAt: Instant? = this.startedAt,
        nextAttemptAt: Instant = this.nextAttemptAt,
        failureCode: ContentFailureCode? = this.failureCode,
        page: CollectedPage? = this.page,
        contentHash: String? = this.contentHash,
        excerpt: String? = this.excerpt,
        collectedAt: Instant? = this.collectedAt,
    ): LinkContent =
        LinkContent(
            id = id,
            linkId = linkId,
            sourceUrl = sourceUrl,
            status = status,
            priority = priority,
            claimCount = claimCount,
            attempt = attempt,
            startedAt = startedAt,
            nextAttemptAt = nextAttemptAt,
            failureCode = failureCode,
            page = page,
            contentHash = contentHash,
            excerpt = excerpt,
            collectedAt = collectedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_CLAIMS = 6
        const val EXCERPT_MAX_CHARS = 100
        const val EXCERPT_MAX_LENGTH = EXCERPT_MAX_CHARS + ELLIPSIS.length
        private const val EXCERPT_SHORT_NUMERATOR = 3
        private const val EXCERPT_SHORT_DENOMINATOR = 10
        val BACKOFF: List<Duration> = listOf(2.minutes, 15.minutes)
        val RETRY_AFTER_MAX: Duration = 900.seconds
        val STALE_AFTER: Duration = 10.minutes
        val RELEASE_DELAY: Duration = 1.minutes

        fun create(
            linkId: Long,
            sourceUrl: String,
            priority: ContentPriority,
            now: Instant,
        ): LinkContent =
            LinkContent(
                linkId = linkId,
                sourceUrl = sourceUrl,
                status = ContentStatus.PENDING,
                priority = priority,
                nextAttemptAt = now,
            )

        fun clampRetryAfter(seconds: Int): Int = seconds.coerceIn(0, RETRY_AFTER_MAX.inWholeSeconds.toInt())

        private fun buildExcerpt(text: String): String? {
            val normalized = text.collapseWhitespace()
            val head =
                normalized
                    .takeKeepingSurrogatePairs(excerptBudget(normalized.length))
                    .trimEnd()
            return head.takeIf { it.isNotEmpty() }?.let { it + ELLIPSIS }
        }

        private fun excerptBudget(normalizedLength: Int): Int =
            if (normalizedLength >= EXCERPT_MAX_CHARS) {
                EXCERPT_MAX_CHARS
            } else {
                normalizedLength * EXCERPT_SHORT_NUMERATOR / EXCERPT_SHORT_DENOMINATOR
            }
    }
}
