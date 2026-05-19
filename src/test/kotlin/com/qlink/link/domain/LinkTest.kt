package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

class LinkTest :
    BehaviorSpec({
        Given("생성 테스트") {
            val ownerId = RandomFixture.randomId()
            val folderId = RandomFixture.randomId()
            val url = RandomFixture.randomUrl()
            val title = RandomFixture.randomSentenceWithMax(300)
            val summary = RandomFixture.randomSentenceWithMax(10_000)
            val tags = RandomFixture.randomSentenceList()
            val thumbnailUrl = RandomFixture.randomUrl()
            val sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)]

            When("링크 생성을") {
                val actual =
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                    )

                Then("성공한다") {
                    actual shouldNotBe null
                    actual.ownerId shouldBe ownerId
                    actual.folderId shouldBe folderId
                    actual.title shouldBe title
                    actual.summary shouldBe summary
                    actual.tags shouldBe tags.distinct()
                    actual.sourceType shouldBe sourceType
                }
            }

            When("중복 태그로 링크를 생성하면") {
                val firstTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val secondTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val thirdTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val duplicatedTags = listOf(firstTag, secondTag, firstTag, thirdTag, secondTag)

                val actual =
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        tags = duplicatedTags,
                        sourceType = sourceType,
                    )

                Then("처음 등장한 순서대로 중복 태그를 제거한다") {
                    actual.tags shouldBe listOf(firstTag, secondTag, thirdTag)
                }
            }

            When("URL이 공백이면") {
                val emptyCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = "",
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }
                val blankCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = " ",
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_BLANK.message) {
                        emptyCreate()
                    }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_BLANK.message) {
                        blankCreate()
                    }
                }
            }

            When("URL의 host가 없으면") {
                val wrongHostCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = "https://.com",
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_WRONG_HOST.message) {
                        wrongHostCreate()
                    }
                }
            }

            When("URL이 웹 프로토콜이 아니면") {
                val notWebCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = "file://google.com",
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_NOT_HTTP.message) {
                        notWebCreate()
                    }
                }
            }

            When("제목이 공백이면") {
                val emptyCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = "",
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }
                val blankCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = " ",
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_TITLE_BLANK.message) {
                        emptyCreate()
                    }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_TITLE_BLANK.message) {
                        blankCreate()
                    }
                }
            }

            When("제목이 최대 300자를 넘으면") {
                val overMaxCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = RandomFixture.randomSentence(301, 400),
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = thumbnailUrl,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_TITLE_OVER_MAX.message) {
                        overMaxCreate()
                    }
                }
            }

            When("썸네일 URL이 있을 때 host가 없으면") {
                val wrongHostCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = "https://.com",
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_WRONG_HOST.message) {
                        wrongHostCreate()
                    }
                }
            }

            When("썸네일 URL이 있을 때 URL이 웹 프로토콜이 아니면") {
                val notWebCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = "file://google.com",
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_NOT_HTTP.message) {
                        notWebCreate()
                    }
                }
            }
        }

        Given("소유자 검증 테스트") {
            val ownerId = RandomFixture.randomId()
            val link = LinkFixture.createRandomLinkOf(ownerId = ownerId)

            When("소유자 검증을") {
                val validate = {
                    link.validateOwner(ownerId)
                }

                Then("성공한다") {
                    shouldNotThrow<BusinessException> {
                        validate()
                    }
                }
            }

            When("다른 소유자로 검증하면") {
                val otherOwnerId = ownerId + 1
                val validate = {
                    link.validateOwner(otherOwnerId)
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        validate()
                    }
                }
            }
        }

        Given("수정 테스트") {
            val ownerId = RandomFixture.randomId()
            val link = LinkFixture.createRandomLinkOf(ownerId = ownerId)

            When("링크 수정을") {
                val folderId = RandomFixture.randomId()
                val url = RandomFixture.randomUrl()
                val title = RandomFixture.randomSentenceWithMax(300)
                val summary = RandomFixture.randomSentenceWithMax(1_000)
                val memo = RandomFixture.randomSentenceWithMax(1_000)
                val tags = RandomFixture.randomSentenceList()
                val thumbnailUrl = RandomFixture.randomUrl()
                val sourceType = SourceType.entries.first { it != link.sourceType }

                val actual =
                    link.update(
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        memo = memo,
                        tags = tags,
                        thumbnailUrl = thumbnailUrl,
                        sourceType = sourceType,
                    )

                Then("성공한다") {
                    actual.id shouldBe link.id
                    actual.ownerId shouldBe link.ownerId
                    actual.folderId shouldBe folderId
                    actual.url shouldBe url
                    actual.title shouldBe title
                    actual.summary shouldBe summary
                    actual.memo shouldBe memo
                    actual.tags shouldBe tags.distinct()
                    actual.thumbnailUrl shouldBe thumbnailUrl
                    actual.sourceType shouldBe sourceType
                }
            }

            When("중복 태그로 링크를 수정하면") {
                val folderId = RandomFixture.randomId()
                val url = RandomFixture.randomUrl()
                val title = RandomFixture.randomSentenceWithMax(300)
                val summary = RandomFixture.randomSentenceWithMax(1_000)
                val memo = RandomFixture.randomSentenceWithMax(1_000)
                val firstTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val secondTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val thirdTag = "${RandomFixture.randomSentenceWithMax(10)}-${RandomFixture.randomId()}"
                val duplicatedTags = listOf(firstTag, secondTag, firstTag, thirdTag, secondTag)
                val thumbnailUrl = RandomFixture.randomUrl()
                val sourceType = SourceType.entries.first { it != link.sourceType }

                val actual =
                    link.update(
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        memo = memo,
                        tags = duplicatedTags,
                        thumbnailUrl = thumbnailUrl,
                        sourceType = sourceType,
                    )

                Then("처음 등장한 순서대로 중복 태그를 제거한다") {
                    actual.tags shouldBe listOf(firstTag, secondTag, thirdTag)
                }
            }

            When("수정 URL이 공백이면") {
                val update = {
                    link.update(
                        folderId = link.folderId,
                        url = "",
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        memo = RandomFixture.randomSentenceWithMax(1_000),
                        tags = RandomFixture.randomSentenceList(),
                        thumbnailUrl = RandomFixture.randomUrl(),
                        sourceType = link.sourceType,
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_BLANK.message) {
                        update()
                    }
                }
            }
        }
    })
