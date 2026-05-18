package com.qlink.support.fixture

import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.link.dto.UpdateLinkRequest
import kotlin.random.Random
import kotlin.time.Instant

object LinkFixture {
    fun createRandomLinkOf(
        ownerId: Long,
        folderId: Long? = null,
        url: String = RandomFixture.randomUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        summary: String? = RandomFixture.randomSentenceWithMax(1000),
        memo: String? = null,
        tags: List<String> = RandomFixture.randomSentenceList(),
        thumbnailUrl: String? = RandomFixture.randomUrl(),
        sourceType: SourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
        reminderAt: Instant? = null,
    ): Link =
        Link(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            thumbnailUrl = thumbnailUrl,
            sourceType = sourceType,
            tags = tags,
            reminderAt = reminderAt,
        )

    fun createValidUpdateLinkRequest(folderId: Long? = null): UpdateLinkRequest =
        UpdateLinkRequest(
            folderId = folderId,
            url = RandomFixture.randomUrl(),
            title = RandomFixture.randomSentenceWithMax(300),
            summary = RandomFixture.randomSentenceWithMax(1000),
            memo = RandomFixture.randomSentenceWithMax(1000),
            tags = RandomFixture.randomSentenceList(),
            thumbnailUrl = RandomFixture.randomUrl(),
            sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
            remindAt = null,
        )
}
