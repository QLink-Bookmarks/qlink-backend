package com.qlink.support.fixture

import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.link.dto.PatchLinkRequest
import com.qlink.link.dto.PatchLinkTodoRequest
import com.qlink.link.dto.UpdateLinkRequest
import com.qlink.todo.domain.RepeatDay
import kotlin.random.Random
import kotlin.time.Instant

object LinkFixture {
    private fun randomDistinctTags(): List<String> = RandomFixture.randomSentenceList().distinct()

    fun createRandomLinkOf(
        ownerId: Long,
        folderId: Long? = null,
        url: String = RandomFixture.randomUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        summary: String? = RandomFixture.randomSentenceWithMax(1000),
        memo: String? = null,
        tags: List<String> = randomDistinctTags(),
        thumbnailUrl: String? = RandomFixture.randomUrl(),
        sourceType: SourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
        workModelId: Long? = null,
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
            workModelId = workModelId,
            tags = tags,
        )

    fun createValidUpdateLinkRequest(folderId: Long? = null): UpdateLinkRequest =
        UpdateLinkRequest(
            folderId = folderId,
            url = RandomFixture.randomUrl(),
            title = RandomFixture.randomSentenceWithMax(300),
            summary = RandomFixture.randomSentenceWithMax(1000),
            memo = RandomFixture.randomSentenceWithMax(1000),
            tags = randomDistinctTags(),
            thumbnailUrl = RandomFixture.randomUrl(),
            sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
        )

    fun createPatchLinkRequest(
        folderId: Long? = null,
        memo: String? = null,
        tags: List<String>? = null,
        todos: List<PatchLinkTodoRequest>? = null,
    ): PatchLinkRequest =
        PatchLinkRequest(
            folderId = folderId,
            memo = memo,
            tags = tags,
            todos = todos,
        )

    fun createPatchLinkTodoRequest(
        id: Long? = null,
        title: String = RandomFixture.randomSentenceWithMax(50),
        reminderAt: Instant? = null,
        repeatUntil: Instant? = null,
        repeatDays: List<RepeatDay>? = null,
        repeatTime: String? = null,
        repeatTimezone: String? = null,
    ): PatchLinkTodoRequest =
        PatchLinkTodoRequest(
            id = id,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
        )
}
