package com.qlink.support.fixture

import com.qlink.ai.domain.AiJob
import com.qlink.ai.domain.AiJobStatus
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
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

    private fun randomAiProviderType(): AiProviderType = AiProviderType.entries[Random.nextInt(AiProviderType.entries.size)]
}
