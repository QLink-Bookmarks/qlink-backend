package com.qlink.support.fixture

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import kotlin.random.Random

object AiFixture {
    fun createRandomValidAiProvider(): AiProvider =
        AiProvider(
            type = AiProviderType.entries[Random.nextInt(AiProviderType.entries.size)],
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
}
