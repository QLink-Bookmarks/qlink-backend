package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import support.fixture.RandomFixture
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
                    actual.tags shouldBe tags
                    actual.sourceType shouldBe sourceType
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
    })
