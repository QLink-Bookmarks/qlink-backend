package com.qlink.linkcontent.domain

import com.qlink.support.fixture.LinkContentFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CollectedPageTest :
    BehaviorSpec({
        Given("Lambda 추출 결과로 페이지를 만들 때") {
            When("본문이 비어 있거나 공백뿐이면") {
                val creates = listOf("", " \n\t  ").map { text -> { LinkContentFixture.createCollectedPage(text = text) } }

                Then("생성이 거부된다") {
                    creates.forEach { create -> shouldThrowWithMessage<IllegalArgumentException>("수집한 본문이 비어 있다") { create() } }
                }
            }

            When("본문이 1자이면") {
                val text = LinkContentFixture.randomBodyText(1)
                val page = LinkContentFixture.createCollectedPage(text = text, isTruncated = false)

                Then("그대로 만들어진다") {
                    page.text shouldBe text
                    page.isTruncated shouldBe false
                }
            }

            When("본문이 100,000자를 넘으면") {
                val text = LinkContentFixture.randomBodyText(CollectedPage.MAX_TEXT_LENGTH + RandomFixture.randomInt(1, 1_000))
                val page = LinkContentFixture.createCollectedPage(text = text, isTruncated = false)

                Then("100,000자에서 잘리고 isTruncated가 참이다") {
                    page.text shouldBe text.take(CollectedPage.MAX_TEXT_LENGTH)
                    page.isTruncated shouldBe true
                }
            }

            When("제목·사이트·작성자가 컬럼 길이를 넘으면") {
                val title = LinkContentFixture.randomBodyText(CollectedPage.MAX_TITLE_LENGTH + 1)
                val siteName = LinkContentFixture.randomBodyText(CollectedPage.MAX_SITE_NAME_LENGTH + 1)
                val author = LinkContentFixture.randomBodyText(CollectedPage.MAX_AUTHOR_LENGTH + 1)
                val page = LinkContentFixture.createCollectedPage(title = title, siteName = siteName, author = author)

                Then("잘려 담긴다") {
                    page.title shouldBe title.take(CollectedPage.MAX_TITLE_LENGTH)
                    page.siteName shouldBe siteName.take(CollectedPage.MAX_SITE_NAME_LENGTH)
                    page.author shouldBe author.take(CollectedPage.MAX_AUTHOR_LENGTH)
                }
            }
        }
    })
