package com.qlink.support.fixture

import com.qlink.ai.client.AiSummaryClientResponse
import com.qlink.ai.client.AiSummaryTodo
import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.dto.AiSummaryCompletion
import com.qlink.ai.dto.AiSummarySnapshot
import com.qlink.link.domain.Link
import kotlin.random.Random
import kotlin.time.Instant

object AiFixture {
    fun createRandomValidAiProvider(type: AiProviderType = randomAiProviderType()): AiProvider =
        AiProvider(
            type = type,
            baseUrl = RandomFixture.randomUrl(),
        )

    fun createRandomAvailableModelOf(providerId: Long): AvailableModel =
        AvailableModel(
            providerId = providerId,
            model = "model-${RandomFixture.randomId()}",
            priority = RandomFixture.randomInt(1, 100),
            rpdLimit = RandomFixture.randomInt(1, 1000),
            tpdLimit = RandomFixture.randomInt(1, 10_000_000),
        )

    fun createAiJobWith(
        requestedAt: Instant,
        status: AiJobStatus = AiJobStatus.P,
        startedAt: Instant? = null,
    ): AiJob =
        AiJob(
            id = RandomFixture.randomId(),
            linkId = RandomFixture.randomId(),
            userProviderId = RandomFixture.randomId(),
            requestModelId = RandomFixture.randomId(),
            requestedUrl = RandomFixture.randomUrl(),
            prompt = RandomFixture.randomSentenceWithMax(1000),
            status = status,
            requestedAt = requestedAt,
            startedAt = startedAt,
        )

    fun createPendingAiJobOf(
        link: Link,
        userProviderId: Long,
        modelId: Long,
    ): AiJob =
        AiJob(
            linkId = link.id!!,
            userProviderId = userProviderId,
            requestModelId = modelId,
            requestedUrl = link.url,
            prompt = RandomFixture.randomSentenceWithMax(1000),
            startedAt = RandomFixture.randomPastInstant(),
        )

    fun createAiSummaryCompletionOf(
        job: AiJob,
        link: Link,
        candidateFolderId: Long?,
    ): AiSummaryCompletion =
        AiSummaryCompletion(
            snapshot =
                AiSummarySnapshot(
                    jobId = job.id!!,
                    linkId = link.id!!,
                    folderId = link.folderId,
                    folderAssignmentRevision = link.folderAssignmentRevision,
                ),
            responseModelId = job.requestModelId,
            candidateFolderId = candidateFolderId,
            response =
                AiSummaryClientResponse(
                    linkId = link.id,
                    folderId = candidateFolderId,
                    rawResponse = RandomFixture.randomSentenceWithMax(1000),
                    title = RandomFixture.randomSentenceWithMax(100),
                    summary = RandomFixture.randomSentenceWithMax(500),
                    tags = listOf(RandomFixture.randomSentenceWithMax(10)),
                    todos =
                        listOf(
                            AiSummaryTodo(
                                title = RandomFixture.randomSentenceWithMax(50),
                                reminderAt = RandomFixture.randomFutureInstantAfterTomorrow(),
                            ),
                        ),
                    usedTokens = RandomFixture.randomInt(1, 1000),
                ),
        )

    private fun randomAiProviderType(): AiProviderType = AiProviderType.entries[Random.nextInt(AiProviderType.entries.size)]
}
