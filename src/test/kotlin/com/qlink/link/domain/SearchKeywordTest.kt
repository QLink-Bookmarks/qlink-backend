package com.qlink.link.domain

import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.text.Normalizer

class SearchKeywordTest :
    BehaviorSpec({
        Given("입력 검색어") {
            When("앞뒤 공백과 대문자, NFD 한글이 섞여 있으면") {
                val latin = RandomFixture.randomSentence(minLength = 3, maxLength = 10).uppercase()
                val raw = "  ${Normalizer.normalize("본문검색", Normalizer.Form.NFD)}$latin \t"
                val keyword = SearchKeyword.of(raw)

                Then("value가 trim·NFC·소문자로 정규화된다") {
                    keyword.value shouldBe "본문검색${latin.lowercase()}"
                }
            }

            When("정규화 길이가 1이면") {
                val keyword = SearchKeyword.of(" 가 ")

                Then("본문을 포함하지 않는다") {
                    keyword.includesBody shouldBe false
                }
            }

            When("정규화 길이가 2이면") {
                val keyword = SearchKeyword.of(" 가나 ")

                Then("본문을 포함한다") {
                    keyword.includesBody shouldBe true
                }
            }

            When("공백뿐이면") {
                val keyword = SearchKeyword.of(" \t\n ")

                Then("비어 있다") {
                    keyword.isBlank shouldBe true
                }
            }
        }
    })
