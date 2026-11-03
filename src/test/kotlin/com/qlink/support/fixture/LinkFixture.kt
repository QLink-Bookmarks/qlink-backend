package com.qlink.support.fixture

import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
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
        id: Long = RandomFixture.randomId(),
        folderId: Long? = null,
        url: String = RandomFixture.randomUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        summary: String? = RandomFixture.randomSentenceWithMax(1000),
        memo: String? = null,
        tags: List<String> = randomDistinctTags(),
        thumbnailUrl: String? = RandomFixture.randomUrl(),
        faviconUrl: String? = null,
        isVideo: Boolean = false,
        sourceType: SourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
        status: LinkStatus = LinkStatus.C,
        workModelId: Long? = null,
        favoriteAt: Instant? = null,
        createdAt: Instant? = null,
    ): Link =
        Link(
            id = id,
            ownerId = ownerId,
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            thumbnailUrl = thumbnailUrl,
            faviconUrl = faviconUrl,
            isVideo = isVideo,
            sourceType = sourceType,
            status = status,
            workModelId = workModelId,
            favoriteAt = favoriteAt,
            tags = tags,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    fun createImportedLinkOf(
        ownerId: Long,
        folderId: Long? = null,
        url: String = RandomFixture.randomUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        createdAt: Instant = RandomFixture.randomPastInstant(),
    ): Link =
        Link(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            folderId = folderId,
            url = url,
            title = title,
            tags = emptyList(),
            sourceType = SourceType.IMPORT,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    fun randomRequestSourceType(): SourceType = SourceType.entries.filter { it != SourceType.IMPORT }.random()

    fun createLinkFromPreviewRequest(
        publicBaseUrl: String = RandomFixture.randomPreviewBaseUrl(),
        environment: String = RandomFixture.randomPreviewEnvironment(),
        thumbnailUrl: String? = RandomFixture.randomPreviewUrl(baseUrl = publicBaseUrl, environment = environment),
        faviconUrl: String? = null,
        isVideo: Boolean = false,
    ): Link =
        Link.createFromRequest(
            ownerId = RandomFixture.randomId(),
            folderId = null,
            url = RandomFixture.randomUrl(),
            title = RandomFixture.randomSentenceWithMax(300),
            summary = null,
            memo = null,
            tags = randomDistinctTags(),
            thumbnailUrl = thumbnailUrl,
            faviconUrl = faviconUrl,
            isVideo = isVideo,
            sourceType = SourceType.INPUT,
            status = LinkStatus.C,
            previewPublicBaseUrl = publicBaseUrl,
            previewEnvironment = environment,
        )

    fun validPreviewUrlOf(
        resolver: PreviewImageUrlResolver,
        kind: String = "preview",
    ): String = RandomFixture.randomPreviewUrl(baseUrl = resolver.publicBaseUrl, environment = resolver.environment, kind = kind)

    fun createValidUpdateLinkRequest(
        folderId: Long? = null,
        thumbnailUrl: String? = null,
    ): UpdateLinkRequest =
        UpdateLinkRequest(
            folderId = folderId,
            url = RandomFixture.randomUrl(),
            title = RandomFixture.randomSentenceWithMax(300),
            summary = RandomFixture.randomSentenceWithMax(1000),
            memo = RandomFixture.randomSentenceWithMax(1000),
            tags = randomDistinctTags(),
            thumbnailUrl = thumbnailUrl,
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
