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
            val sourceType = LinkFixture.randomRequestSourceType()
            val previewBaseUrl = RandomFixture.randomPreviewBaseUrl()
            val previewEnvironment = RandomFixture.randomPreviewEnvironment()

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

            When("URL에 스킴이 없으면") {
                val noSchemeCreate = {
                    Link(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = "//example-${RandomFixture.randomId()}.com/x",
                        title = title,
                        summary = summary,
                        tags = tags,
                        sourceType = sourceType,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_NOT_HTTP.message) {
                        noSchemeCreate()
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
                    Link.createFromRequest(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        memo = null,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = "https://.com",
                        faviconUrl = null,
                        isVideo = false,
                        status = LinkStatus.C,
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
                        wrongHostCreate()
                    }
                }
            }

            When("썸네일 URL이 있을 때 URL이 웹 프로토콜이 아니면") {
                val notWebCreate = {
                    Link.createFromRequest(
                        ownerId = ownerId,
                        folderId = folderId,
                        url = url,
                        title = title,
                        summary = summary,
                        memo = null,
                        tags = tags,
                        sourceType = sourceType,
                        thumbnailUrl = "file://google.com",
                        faviconUrl = null,
                        isVideo = false,
                        status = LinkStatus.C,
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
                    )
                }

                Then("생성을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
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
            val previewBaseUrl = RandomFixture.randomPreviewBaseUrl()
            val previewEnvironment = RandomFixture.randomPreviewEnvironment()

            When("링크 수정을") {
                val folderId = RandomFixture.randomId()
                val url = RandomFixture.randomUrl()
                val title = RandomFixture.randomSentenceWithMax(300)
                val summary = RandomFixture.randomSentenceWithMax(1_000)
                val memo = RandomFixture.randomSentenceWithMax(1_000)
                val tags = RandomFixture.randomSentenceList()
                val thumbnailUrl = RandomFixture.randomPreviewUrl(baseUrl = previewBaseUrl, environment = previewEnvironment)
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
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
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
                val thumbnailUrl = link.thumbnailUrl
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
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
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
                        thumbnailUrl = link.thumbnailUrl,
                        sourceType = link.sourceType,
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
                    )
                }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_URL_BLANK.message) {
                        update()
                    }
                }
            }
        }

        Given("가져온 링크를 만들 때") {
            val ownerId = RandomFixture.randomId()
            val folderId = RandomFixture.randomId()
            val url = RandomFixture.randomUrl()
            val title = RandomFixture.randomSentenceWithMax(300)
            val createdAt = RandomFixture.randomPastInstant()

            When("유효하면") {
                val link = Link.createImported(ownerId = ownerId, folderId = folderId, url = url, title = title, createdAt = createdAt)

                Then("sourceType=IMPORT, status=C, createdAt·updatedAt이 주어진 시각, 주어진 폴더") {
                    link.sourceType shouldBe SourceType.IMPORT
                    link.status shouldBe LinkStatus.C
                    link.createdAt shouldBe createdAt
                    link.updatedAt shouldBe createdAt
                    link.folderId shouldBe folderId
                    link.ownerId shouldBe ownerId
                    link.url shouldBe url
                    link.title shouldBe title
                }
            }
        }

        Given("요청으로 링크를 만들 때") {
            val previewBaseUrl = RandomFixture.randomPreviewBaseUrl()
            val previewEnvironment = RandomFixture.randomPreviewEnvironment()

            fun createFromRequest(sourceType: SourceType): Link =
                Link.createFromRequest(
                    ownerId = RandomFixture.randomId(),
                    folderId = null,
                    url = RandomFixture.randomUrl(),
                    title = RandomFixture.randomSentenceWithMax(300),
                    summary = null,
                    memo = null,
                    tags = emptyList(),
                    thumbnailUrl = null,
                    faviconUrl = null,
                    isVideo = false,
                    sourceType = sourceType,
                    status = LinkStatus.C,
                    previewPublicBaseUrl = previewBaseUrl,
                    previewEnvironment = previewEnvironment,
                )

            When("sourceType이 IMPORT면") {
                val create = { createFromRequest(SourceType.IMPORT) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_SOURCE_TYPE_NOT_ALLOWED.message) {
                        create()
                    }
                }
            }

            When("sourceType이 INPUT·QR·COPY면") {
                val links = listOf(SourceType.INPUT, SourceType.QR, SourceType.COPY).map(::createFromRequest)

                Then("링크가 만들어진다") {
                    links.map { it.sourceType } shouldBe listOf(SourceType.INPUT, SourceType.QR, SourceType.COPY)
                }
            }
        }

        Given("sourceType=IMPORT인 링크") {
            val link = LinkFixture.createImportedLinkOf(ownerId = RandomFixture.randomId())
            val previewBaseUrl = RandomFixture.randomPreviewBaseUrl()
            val previewEnvironment = RandomFixture.randomPreviewEnvironment()

            When("sourceType=IMPORT로 수정하면") {
                val updated =
                    link.update(
                        folderId = link.folderId,
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = null,
                        memo = RandomFixture.randomSentenceWithMax(100),
                        tags = emptyList(),
                        thumbnailUrl = null,
                        sourceType = SourceType.IMPORT,
                        previewPublicBaseUrl = previewBaseUrl,
                        previewEnvironment = previewEnvironment,
                    )

                Then("IMPORT가 유지된다") {
                    updated.sourceType shouldBe SourceType.IMPORT
                }
            }
        }

        Given("미리보기를 채울 링크") {
            val ownerId = RandomFixture.randomId()
            val thumbnailUrl = RandomFixture.randomUrl()
            val faviconUrl = RandomFixture.randomUrl()

            When("썸네일·파비콘이 둘 다 비었으면") {
                val link = LinkFixture.createImportedLinkOf(ownerId = ownerId)
                val filled = link.fillPreview(thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl, isVideo = true)

                Then("thumbnailUrl·faviconUrl·isVideo가 채워지고 제목은 그대로") {
                    filled.thumbnailUrl shouldBe thumbnailUrl
                    filled.faviconUrl shouldBe faviconUrl
                    filled.isVideo shouldBe true
                    filled.title shouldBe link.title
                    filled.id shouldBe link.id
                    filled.createdAt shouldBe link.createdAt
                }
            }

            When("썸네일이 이미 있으면") {
                val link = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = RandomFixture.randomUrl(), faviconUrl = null)
                val filled = link.fillPreview(thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl, isVideo = true)

                Then("링크가 바뀌지 않는다") {
                    filled shouldBe link
                }
            }

            When("파비콘이 이미 있으면") {
                val link = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = null, faviconUrl = RandomFixture.randomUrl())
                val filled = link.fillPreview(thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl, isVideo = true)

                Then("링크가 바뀌지 않는다") {
                    filled shouldBe link
                }
            }
        }
    })
