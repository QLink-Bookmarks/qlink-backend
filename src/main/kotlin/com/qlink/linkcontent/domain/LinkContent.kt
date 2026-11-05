@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.domain

import com.qlink.common.text.collapseWhitespace
import com.qlink.common.text.sha256Hex
import com.qlink.common.text.takeKeepingSurrogatePairs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val ELLIPSIS = "…"

class LinkContent(
    val id: Long? = null,
    val linkId: Long,
    val sourceUrl: String,
    val status: ContentStatus,
    val priority: ContentPriority,
    val generation: Int = 0,
    val claimToken: Uuid? = null,
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

    val fence: ClaimFence
        get() =
            ClaimFence(
                id = checkNotNull(id) { "저장되지 않은 본문 행에는 fence가 없다" },
                generation = generation,
                claimToken = checkNotNull(claimToken) { "클레임되지 않은 본문 행에는 fence가 없다" },
            )

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
            claimToken = null,
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
                    claimToken = null,
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
            claimToken = null,
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
            priority = priority,
            generation = generation + 1,
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
            claimToken = null,
            attempt = attempt,
            startedAt = null,
            failureCode = code,
        )

    private fun retryDelay(retryAfterSeconds: Int?): Duration {
        val backoff = BACKOFF[attempt.coerceAtMost(BACKOFF.lastIndex)]
        val retryAfter = (retryAfterSeconds ?: 0).coerceIn(0, RETRY_AFTER_MAX.inWholeSeconds.toInt()).seconds
        return maxOf(backoff, retryAfter)
    }

    private fun validateCollected() {
        val text = requireNotNull(page) { "수집된 본문 행에는 본문이 있어야 한다" }.text
        requireNotNull(contentHash) { "수집된 본문 행에는 해시가 있어야 한다" }
        requireNotNull(collectedAt) { "수집된 본문 행에는 수집 시각이 있어야 한다" }

        val normalizedLength = text.collapseWhitespace().length
        when (excerpt) {
            null -> {
                require(buildExcerpt(text) == null) { "발췌를 만들 수 있는 본문에는 발췌가 있어야 한다" }
            }

            else -> {
                require(excerpt.endsWith(ELLIPSIS)) { "발췌는 말줄임으로 끝나야 한다" }
                require(excerpt.length <= EXCERPT_MAX_CHARS + ELLIPSIS.length) { "발췌가 최대 길이를 넘는다" }
                require(excerpt.length - ELLIPSIS.length <= excerptBudget(normalizedLength)) { "발췌가 본문 예산을 넘는다" }
                require(buildExcerpt(text) != null) { "발췌를 만들 수 없는 본문에는 발췌가 없어야 한다" }
            }
        }
    }

    private fun validateNotCollected() {
        require(page == null && contentHash == null && excerpt == null && collectedAt == null) {
            "수집되지 않은 본문 행에는 본문·발췌가 없어야 한다"
        }
    }

    private fun copy(
        status: ContentStatus = this.status,
        claimToken: Uuid? = this.claimToken,
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
            generation = generation,
            claimToken = claimToken,
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
