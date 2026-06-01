package com.qlink.support.fixture

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel

object AiFixture {
    fun createRandomValidAiProvider(excludingTypes: Set<AiProviderType> = emptySet()): AiProvider {
        val candidates = AiProviderType.entries.filterNot { it in excludingTypes }

        return AiProvider(
            type = candidates[RandomFixture.randomInt(0, candidates.lastIndex)],
            baseUrl = RandomFixture.randomUrl(),
        )
    }

    fun createRandomAvailableModelOf(providerId: Long): AvailableModel =
        AvailableModel(
            providerId = providerId,
            model = "model-${RandomFixture.randomId()}",
            priority = RandomFixture.randomInt(1, 100),
            rpdLimit = RandomFixture.randomInt(1, 1000),
            tpdLimit = RandomFixture.randomInt(1, 10_000_000),
        )
}
