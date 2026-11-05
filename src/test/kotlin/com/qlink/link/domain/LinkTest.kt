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

        Given("가져올 때 url·title과 비교할 링크") {
            val ownerId = RandomFixture.randomId()
            val importedUrl = RandomFixture.randomUrl()
            val importedTitle = RandomFixture.randomSentenceWithMax(200)

            fun linkOf(
                url: String = importedUrl,
                title: String = importedTitle,
                memo: String? = null,
                tags: List<String> = emptyList(),
            ): Link = LinkFixture.createRandomLinkOf(ownerId = ownerId, url = url, title = title, memo = memo, tags = tags)

            When("url이나 title이 다르면") {
                val urlChanged = linkOf(url = RandomFixture.randomUrl()).isEditedFrom(url = importedUrl, title = importedTitle)
                val titleChanged =
                    linkOf(
                        title = "$importedTitle ${RandomFixture.randomId()}",
                    ).isEditedFrom(url = importedUrl, title = importedTitle)

                Then("true") {
                    urlChanged shouldBe true
                    titleChanged shouldBe true
                }
            }

            When("url·title이 같고 memo·tags만 있으면") {
                val edited =
                    linkOf(memo = RandomFixture.randomSentenceWithMax(100), tags = listOf("tag-${RandomFixture.randomId()}"))
                        .isEditedFrom(url = importedUrl, title = importedTitle)

                Then("false") {
                    edited shouldBe false
                }
            }

            When("즐겨찾기·폴더·썸네일·AI 요약만 다르면") {
                val current =
                    LinkFixture.createRandomLinkOf(
                        ownerId = ownerId,
                        folderId = RandomFixture.randomId(),
                        url = importedUrl,
                        title = importedTitle,
                        summary = RandomFixture.randomSentenceWithMax(100),
                        tags = emptyList(),
                        thumbnailUrl = RandomFixture.randomUrl(),
                        faviconUrl = RandomFixture.randomUrl(),
                        favoriteAt = RandomFixture.randomPastInstant(),
                    )
                val edited = current.isEditedFrom(url = importedUrl, title = importedTitle)

                Then("false") {
                    edited shouldBe false
                }
            }
        }

        Given("사용자가 넣는 memo·tags 상태가 다른 링크") {
            val ownerId = RandomFixture.randomId()

            When("memo가 있으면") {
                val hasUserContent =
                    LinkFixture
                        .createRandomLinkOf(ownerId = ownerId, memo = RandomFixture.randomSentenceWithMax(100), tags = emptyList())
                        .hasUserContent()

                Then("true") {
                    hasUserContent shouldBe true
                }
            }

            When("tags가 있으면") {
                val hasUserContent =
                    LinkFixture
                        .createRandomLinkOf(ownerId = ownerId, memo = null, tags = listOf("tag-${RandomFixture.randomId()}"))
                        .hasUserContent()

                Then("true") {
                    hasUserContent shouldBe true
                }
            }

            When("memo·tags가 둘 다 비었으면") {
                val hasUserContent = LinkFixture.createRandomLinkOf(ownerId = ownerId, memo = "", tags = emptyList()).hasUserContent()

                Then("false") {
                    hasUserContent shouldBe false
                }
            }
        }

        Given("미리보기 상태가 다른 링크") {
            val ownerId = RandomFixture.randomId()

            When("썸네일이 있으면") {
                val hasPreview =
                    LinkFixture
                        .createRandomLinkOf(
                            ownerId = ownerId,
                            thumbnailUrl = RandomFixture.randomUrl(),
                            faviconUrl = null,
                        ).hasPreview

                Then("true") {
                    hasPreview shouldBe true
                }
            }

            When("파비콘만 있으면") {
                val hasPreview =
                    LinkFixture
                        .createRandomLinkOf(
                            ownerId = ownerId,
                            thumbnailUrl = null,
                            faviconUrl = RandomFixture.randomUrl(),
                        ).hasPreview

                Then("true") {
                    hasPreview shouldBe true
                }
            }

            When("둘 다 없으면") {
                val hasPreview = LinkFixture.createRandomLinkOf(ownerId = ownerId, thumbnailUrl = null, faviconUrl = null).hasPreview

                Then("false") {
                    hasPreview shouldBe false
                }
            }
        }
        Given("링크 소유자와 시작 folder assignment revision") {
            val ownerId = RandomFixture.randomId()
            val revision = RandomFixture.randomLong(1, Long.MAX_VALUE - 1)
            val link =
                LinkFixture.createRandomLinkOf(
                    ownerId = ownerId,
                    folderId = RandomFixture.randomId(),
                    thumbnailUrl = null,
                    folderAssignmentRevision = revision,
                )
            When("소유자를 비교하면") {
                Then("true다") { link.isOwnedBy(ownerId) shouldBe true }
            }
            When("다른 사용자를 비교하면") {
                Then("false다") { link.isOwnedBy(RandomFixture.randomId()) shouldBe false }
            }
            When("시작 revision이 같으면") {
                Then("배정이 바뀌지 않았다") { link.hasFolderAssignmentChangedSince(revision) shouldBe false }
            }
            When("시작 revision보다 커졌고 현재 folderId는 시작과 같으면") {
                val start =
                    LinkFixture.createRandomLinkOf(
                        ownerId = ownerId,
                        folderId = link.folderId,
                        folderAssignmentRevision = revision - 1,
                    )
                Then("배정 변경 이력이 있다") {
                    link.folderId shouldBe start.folderId
                    link.hasFolderAssignmentChangedSince(start.folderAssignmentRevision) shouldBe true
                }
            }
            When("폴더 배정 없이 링크 내용을 수정하면") {
                val updated =
                    link.update(
                        folderId = link.folderId,
                        url = link.url,
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = link.summary,
                        memo = link.memo,
                        tags = link.tags,
                        thumbnailUrl = link.thumbnailUrl,
                        sourceType = link.sourceType,
                        previewPublicBaseUrl = RandomFixture.randomPreviewBaseUrl(),
                        previewEnvironment = RandomFixture.randomPreviewEnvironment(),
                    )
                Then("DB revision metadata를 보존한다") {
                    updated.folderAssignmentRevision shouldBe revision
                    updated.folderId shouldBe link.folderId
                }
            }
            When("즐겨찾기나 미리보기를 변경하면") {
                val favorite = link.changeFavorite(isFavorite = true, now = RandomFixture.randomPastInstant())!!
                val preview = link.fillPreview(thumbnailUrl = RandomFixture.randomUrl(), faviconUrl = null, isVideo = false)
                Then("DB revision metadata를 보존한다") {
                    favorite.folderAssignmentRevision shouldBe revision
                    preview.folderAssignmentRevision shouldBe revision
                }
            }
        }
    })
