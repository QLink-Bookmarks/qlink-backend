@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.support.fixture

import com.qlink.common.text.sha256Hex
import com.qlink.linkcontent.client.LinkContentExtraction
import com.qlink.linkcontent.client.LinkContentResult
import com.qlink.linkcontent.domain.CollectedPage
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object LinkContentFixture {
    fun randomPriority(): ContentPriority = ContentPriority.entries.random()

    fun randomBodyText(length: Int = RandomFixture.randomInt(100, 2_000)): String = RandomFixture.randomFixedSentence(length)

    fun createCollectedPage(
        text: String = randomBodyText(),
        title: String? = RandomFixture.randomSentenceWithMax(100),
        siteName: String? = RandomFixture.randomSentenceWithMax(50),
        author: String? = RandomFixture.randomSentenceWithMax(50),
        publishedAt: Instant? = RandomFixture.randomPastInstant(),
        canonicalUrl: String? = RandomFixture.randomUrl(),
        finalUrl: String? = RandomFixture.randomUrl(),
        language: String? = "ko",
        isTruncated: Boolean = false,
    ): CollectedPage =
        CollectedPage(
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

    fun createPendingContentOf(
        linkId: Long,
        sourceUrl: String = RandomFixture.randomUrl(),
        priority: ContentPriority = randomPriority(),
        nextAttemptAt: Instant = RandomFixture.randomPastInstant(),
    ): LinkContent =
        LinkContent.create(
            linkId = linkId,
            sourceUrl = sourceUrl,
            priority = priority,
            now = nextAttemptAt,
        )

    fun createClaimedContentOf(
        linkId: Long = RandomFixture.randomId(),
        id: Long = RandomFixture.randomId(),
        sourceUrl: String = RandomFixture.randomUrl(),
        priority: ContentPriority = randomPriority(),
        generation: Int = RandomFixture.randomInt(0, 5),
        claimToken: Uuid = Uuid.random(),
        claimCount: Int = RandomFixture.randomInt(1, LinkContent.MAX_CLAIMS),
        attempt: Int = 0,
        startedAt: Instant = RandomFixture.randomPastInstant(),
        nextAttemptAt: Instant = startedAt,
    ): LinkContent =
        LinkContent(
            id = id,
            linkId = linkId,
            sourceUrl = sourceUrl,
            status = ContentStatus.PENDING,
            priority = priority,
            generation = generation,
            claimToken = claimToken,
            claimCount = claimCount,
            attempt = attempt,
            startedAt = startedAt,
            nextAttemptAt = nextAttemptAt,
        )

    fun createCollectedContentOf(
        linkId: Long = RandomFixture.randomId(),
        id: Long = RandomFixture.randomId(),
        sourceUrl: String = RandomFixture.randomUrl(),
        text: String = randomBodyText(),
        generation: Int = RandomFixture.randomInt(0, 5),
        collectedAt: Instant = RandomFixture.randomPastInstant(),
    ): LinkContent =
        createClaimedContentOf(linkId = linkId, id = id, sourceUrl = sourceUrl, generation = generation)
            .complete(page = createCollectedPage(text = text), now = collectedAt)

    fun createContentWithExcerpt(
        status: ContentStatus,
        text: String?,
        excerpt: String?,
        contentHash: String? = text?.sha256Hex(),
        collectedAt: Instant? = text?.let { RandomFixture.randomPastInstant() },
    ): LinkContent =
        LinkContent(
            id = RandomFixture.randomId(),
            linkId = RandomFixture.randomId(),
            sourceUrl = RandomFixture.randomUrl(),
            status = status,
            priority = randomPriority(),
            nextAttemptAt = RandomFixture.randomPastInstant(),
            page = text?.let { createCollectedPage(text = it) },
            contentHash = contentHash,
            excerpt = excerpt,
            collectedAt = collectedAt,
        )

    fun collectedResult(
        text: String = randomBodyText(),
        title: String? = RandomFixture.randomSentenceWithMax(100),
        finalUrl: String? = RandomFixture.randomUrl(),
    ): LinkContentExtraction.Collected =
        LinkContentExtraction.Collected(
            LinkContentResult(
                text = text,
                title = title,
                finalUrl = finalUrl,
                language = "ko",
            ),
        )

    fun failedResult(
        code: ContentFailureCode,
        upstreamStatus: Int? = null,
        retryAfterSeconds: Int? = null,
    ): LinkContentExtraction.Failed =
        LinkContentExtraction.Failed(
            code = code,
            upstreamStatus = upstreamStatus,
            retryAfterSeconds = retryAfterSeconds,
        )
}
