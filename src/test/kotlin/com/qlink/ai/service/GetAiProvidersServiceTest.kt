package com.qlink.ai.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

class GetAiProvidersServiceTest :
    BaseServiceTest({
        val service = koinGet<GetAiProvidersService>()
        val aiProviderRepository = koinGet<AiProviderRepository>()

        suspend fun insertProvider(type: AiProviderType): AiProvider =
            aiProviderRepository.findByType(type)
                ?: aiProviderRepository.insert(
                    AiProvider(
                        type = type,
                        baseUrl = RandomFixture.randomUrl(),
                    ),
                )

        Given("AI Provider 목록 조회 서비스 테스트") {
            When("등록된 provider가 있으면") {
                Then("모든 provider의 id와 type을 반환한다") {
                    val geminiProvider = insertProvider(AiProviderType.GEMINI)
                    val openAiProvider = insertProvider(AiProviderType.OPENAI)

                    val response = service.getAiProviders()

                    response shouldHaveSize 2
                    response.map { it.id } shouldContainExactly listOf(geminiProvider.id, openAiProvider.id)
                    response.map { it.type } shouldContainExactly listOf(AiProviderType.GEMINI, AiProviderType.OPENAI)
                }
            }

            When("등록된 provider가 없으면") {
                Then("빈 목록을 반환한다") {
                    val response = service.getAiProviders()

                    response.shouldBeEmpty()
                }
            }
        }
    })
