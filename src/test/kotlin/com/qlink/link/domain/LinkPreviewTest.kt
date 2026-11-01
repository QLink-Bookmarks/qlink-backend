package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.net.URI

class LinkPreviewTest :
    BehaviorSpec({
        val cdn = RandomFixture.randomPreviewBaseUrl()
        val host = URI(cdn).host
        val environment = RandomFixture.randomPreviewEnvironment()
        val otherEnvironment = listOf("dev", "prod").first { it != environment }

        fun previewUrl(kind: String = "preview"): String =
            RandomFixture.randomPreviewUrl(baseUrl = cdn, environment = environment, kind = kind)

        fun createFromRequest(
            thumbnailUrl: String?,
            faviconUrl: String? = null,
            isVideo: Boolean = false,
        ): Link =
            LinkFixture.createLinkFromPreviewRequest(
                publicBaseUrl = cdn,
                environment = environment,
                thumbnailUrl = thumbnailUrl,
                faviconUrl = faviconUrl,
                isVideo = isVideo,
            )

        fun Link.updatePreview(
            thumbnailUrl: String?,
            faviconUrl: String?,
            isVideo: Boolean,
        ): Link =
            update(
                folderId = folderId,
                url = url,
                title = title,
                summary = summary,
                memo = memo,
                tags = tags,
                thumbnailUrl = thumbnailUrl,
                faviconUrl = faviconUrl,
                isVideo = isVideo,
                sourceType = sourceType,
                previewPublicBaseUrl = cdn,
                previewEnvironment = environment,
            )

        Given("링크 요청으로 이미지 값을 만들 때") {
            When("두 필드에 preview와 favicon 접두어를 교차해 넣으면") {
                val thumbnailUrl = previewUrl(kind = "favicon")
                val faviconUrl = previewUrl()
                val link = createFromRequest(thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl, isVideo = true)

                Then("세 값이 저장된다") {
                    link.thumbnailUrl shouldBe thumbnailUrl
                    link.faviconUrl shouldBe faviconUrl
                    link.isVideo shouldBe true
                }
            }

            When("이미지와 영상 여부를 생략하면") {
                val link = createFromRequest(thumbnailUrl = null)

                Then("URL은 비어 있고 영상이 아니다") {
                    link.thumbnailUrl shouldBe null
                    link.faviconUrl shouldBe null
                    link.isVideo shouldBe false
                }
            }

            When("CDN의 scheme, host, port 중 하나가 다르면") {
                val invalidUrls =
                    listOf(
                        "http://$host/$environment-preview-image.png",
                        "https://other-$host/$environment-preview-image.png",
                        "https://$host:8443/$environment-preview-image.png",
                    )
                val create = { value: String -> createFromRequest(thumbnailUrl = value) }

                Then("썸네일 URL 오류가 난다") {
                    invalidUrls.forEach { value ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
                            create(value)
                        }
                    }
                }
            }

            When("userinfo, query, fragment 중 하나가 있으면") {
                val invalidUrls =
                    listOf(
                        "https://user@$host/$environment-preview-image.png",
                        "$cdn/$environment-preview-image.png?x=1",
                        "$cdn/$environment-preview-image.png#section",
                    )
                val create = { value: String -> createFromRequest(thumbnailUrl = value) }

                Then("썸네일 URL 오류가 난다") {
                    invalidUrls.forEach { value ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
                            create(value)
                        }
                    }
                }
            }

            When("파일명이 하위 경로이거나 인코딩된 구분자를 포함하면") {
                val invalidUrls =
                    listOf(
                        "$cdn/nested/$environment-preview-image.png",
                        "$cdn/$environment-preview-a%2Fb.png",
                        "$cdn/$environment-preview-a%5Cb.png",
                    )
                val create = { value: String -> createFromRequest(thumbnailUrl = value) }

                Then("썸네일 URL 오류가 난다") {
                    invalidUrls.forEach { value ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
                            create(value)
                        }
                    }
                }
            }

            When("파일명 접미부가 없거나 다른 환경 또는 용도이면") {
                val invalidUrls =
                    listOf(
                        "$cdn/$environment-preview-",
                        "$cdn/$otherEnvironment-preview-image.png",
                        "$cdn/$environment-profile-image.png",
                        "$cdn/$environment-post-image.png",
                    )
                val create = { value: String -> createFromRequest(thumbnailUrl = value) }

                Then("썸네일 URL 오류가 난다") {
                    invalidUrls.forEach { value ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) {
                            create(value)
                        }
                    }
                }
            }

            When("파비콘 URL이 허용되지 않으면") {
                val create = { createFromRequest(thumbnailUrl = null, faviconUrl = RandomFixture.randomUrl()) }

                Then("파비콘 URL 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED.message) { create() }
                }
            }
        }

        Given("저장된 이미지 값이 있는 링크") {
            val legacyThumbnail = RandomFixture.randomUrl()
            val link =
                LinkFixture.createRandomLinkOf(
                    ownerId = RandomFixture.randomId(),
                    thumbnailUrl = legacyThumbnail,
                    faviconUrl = RandomFixture.randomUrl(),
                    isVideo = true,
                )

            When("이미지 값을 그대로 전달해 수정하면") {
                val updated = link.updatePreview(thumbnailUrl = link.thumbnailUrl, faviconUrl = link.faviconUrl, isVideo = link.isVideo)

                Then("기존 URL을 재검증하지 않는다") {
                    updated.thumbnailUrl shouldBe link.thumbnailUrl
                    updated.faviconUrl shouldBe link.faviconUrl
                    updated.isVideo shouldBe true
                }
            }

            When("페이지 URL만 바꾸고 이미지 값은 그대로 전달하면") {
                val newPageUrl = RandomFixture.randomUrl()
                val updated =
                    link.update(
                        folderId = link.folderId,
                        url = newPageUrl,
                        title = link.title,
                        summary = link.summary,
                        memo = link.memo,
                        tags = link.tags,
                        thumbnailUrl = link.thumbnailUrl,
                        faviconUrl = link.faviconUrl,
                        isVideo = link.isVideo,
                        sourceType = link.sourceType,
                        previewPublicBaseUrl = cdn,
                        previewEnvironment = environment,
                    )

                Then("페이지 URL만 바뀌고 기존 이미지 값은 유지된다") {
                    updated.url shouldBe newPageUrl
                    updated.thumbnailUrl shouldBe link.thumbnailUrl
                    updated.faviconUrl shouldBe link.faviconUrl
                    updated.isVideo shouldBe link.isVideo
                }
            }

            When("새 유효한 썸네일 URL로 바꾸면") {
                val newUrl = previewUrl()
                val updated = link.updatePreview(thumbnailUrl = newUrl, faviconUrl = link.faviconUrl, isVideo = link.isVideo)

                Then("새 URL이 저장된다") {
                    updated.thumbnailUrl shouldBe newUrl
                }
            }

            When("새 잘못된 썸네일 URL로 바꾸면") {
                val update = {
                    link.updatePreview(
                        thumbnailUrl = RandomFixture.randomUrl(),
                        faviconUrl = link.faviconUrl,
                        isVideo = link.isVideo,
                    )
                }

                Then("썸네일 URL 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED.message) { update() }
                }
            }

            When("새 잘못된 파비콘 URL로 바꾸면") {
                val update = {
                    link.updatePreview(
                        thumbnailUrl = link.thumbnailUrl,
                        faviconUrl = "$cdn/$environment-profile-image.png",
                        isVideo = link.isVideo,
                    )
                }

                Then("파비콘 URL 오류가 난다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED.message) { update() }
                }
            }

            When("이미지 값을 지우면") {
                val updated = link.updatePreview(thumbnailUrl = null, faviconUrl = null, isVideo = false)

                Then("URL은 null이고 영상이 아니다") {
                    updated.thumbnailUrl shouldBe null
                    updated.faviconUrl shouldBe null
                    updated.isVideo shouldBe false
                }
            }

            When("복사하면") {
                val copied = link.addToFolder(newOwnerId = RandomFixture.randomId(), folderId = null)

                Then("이미지 값이 유지된다") {
                    copied.thumbnailUrl shouldBe link.thumbnailUrl
                    copied.faviconUrl shouldBe link.faviconUrl
                    copied.isVideo shouldBe link.isVideo
                }
            }

            When("즐겨찾기를 바꾸면") {
                val changed = link.changeFavorite(isFavorite = true)

                Then("이미지 값이 유지된다") {
                    changed?.thumbnailUrl shouldBe link.thumbnailUrl
                    changed?.faviconUrl shouldBe link.faviconUrl
                    changed?.isVideo shouldBe link.isVideo
                }
            }
        }
    })
