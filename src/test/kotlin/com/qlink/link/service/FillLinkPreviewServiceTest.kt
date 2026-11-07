package com.qlink.link.service

import com.qlink.folder.repository.FolderRepository
import com.qlink.link.client.LinkPreviewCaller
import com.qlink.link.client.LinkPreviewExtraction
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FakeLinkPreviewSyncClient
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.LinkPreviewFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private const val PUBLIC_BASE_URL = "https://images.archivelink.app"

class FillLinkPreviewServiceTest :
    BaseServiceTest({
        val service = koinGet<FillLinkPreviewService>()
        val client = koinGet<FakeLinkPreviewSyncClient>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()

        suspend fun insertLinkOf(
            thumbnailUrl: String? = null,
            faviconUrl: String? = null,
        ): Link {
            val user = userRepository.insert(UserFixture.createRandomValidUser())
            val folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(ownerId = user.id!!))
            return linkRepository.insert(
                LinkFixture.createRandomLinkOf(
                    ownerId = user.id,
                    folderId = folder.id,
                    memo = RandomFixture.randomSentenceWithMax(100),
                    thumbnailUrl = thumbnailUrl,
                    faviconUrl = faviconUrl,
                    isVideo = RandomFixture.randomInt(0, 1) == 1,
                    status = LinkStatus.entries.random(),
                ),
            )
        }

        Given("썸네일·파비콘이 모두 빈 링크") {
            lateinit var link: Link

            beforeEach {
                client.reset()
                link = insertLinkOf()
            }

            When("Lambda가 썸네일을 돌려주면") {
                val thumbnailKey = LinkPreviewFixture.key(kind = "preview")
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = thumbnailKey, isVideo = !link.isVideo))
                        service.fill(link.id!!)
                    }

                Then("thumbnail_url이 현재 환경 CDN URL로, is_video가 결과 값으로 저장되고 favicon_url은 비어 있으며 Lambda는 PREVIEW·LINK_FILL로 한 번 불린다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe "$PUBLIC_BASE_URL/$thumbnailKey"
                    actual.isVideo shouldBe !link.isVideo
                    actual.faviconUrl shouldBe null
                    client.calls.single() shouldBe (link.url to LinkPreviewMode.PREVIEW)
                    client.callers.single() shouldBe LinkPreviewCaller.LINK_FILL
                }
            }

            When("Lambda가 썸네일과 파비콘을 함께 돌려주면") {
                val thumbnailKey = LinkPreviewFixture.key(kind = "preview")
                val fill =
                    suspend {
                        client.respond(
                            LinkPreviewResult(thumbnailKey = thumbnailKey, faviconKey = LinkPreviewFixture.key(kind = "favicon")),
                        )
                        service.fill(link.id!!)
                    }

                Then("thumbnail_url만 저장되고 favicon_url은 비어 있다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe "$PUBLIC_BASE_URL/$thumbnailKey"
                    actual.faviconUrl shouldBe null
                }
            }

            When("Lambda가 파비콘만 돌려주면") {
                val faviconKey = LinkPreviewFixture.key(kind = "favicon")
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(faviconKey = faviconKey, isVideo = !link.isVideo))
                        service.fill(link.id!!)
                    }

                Then("favicon_url이 저장되고 thumbnail_url은 비어 있으며 is_video는 결과 값으로 저장된다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.faviconUrl shouldBe "$PUBLIC_BASE_URL/$faviconKey"
                    actual.thumbnailUrl shouldBe null
                    actual.isVideo shouldBe !link.isVideo
                }
            }

            When("Lambda가 키 없는 성공을 돌려주면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(isVideo = !link.isVideo))
                        service.fill(link.id!!)
                    }

                Then("thumbnail_url·favicon_url·is_video가 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe link.isVideo
                }
            }

            When("Lambda가 현재 환경 형식이 아닌 키만 돌려주면") {
                val fill =
                    suspend {
                        client.respond(
                            LinkPreviewResult(
                                thumbnailKey = LinkPreviewFixture.key(kind = "preview", environment = "prod"),
                                faviconKey = LinkPreviewFixture.key(kind = "favicon", environment = "prod"),
                                isVideo = !link.isVideo,
                            ),
                        )
                        service.fill(link.id!!)
                    }

                Then("thumbnail_url·favicon_url·is_video가 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe link.isVideo
                }
            }

            When("Lambda 호출이 실패하면") {
                val fill =
                    suspend {
                        client.extraction = LinkPreviewExtraction.Failed
                        service.fill(link.id!!)
                    }

                Then("링크가 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe link.isVideo
                }
            }

            When("Lambda가 스로틀링을 알리면") {
                val fill =
                    suspend {
                        client.extraction = LinkPreviewExtraction.Throttled
                        service.fill(link.id!!)
                    }

                Then("링크가 그대로이고 Lambda는 한 번만 불린다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe link.isVideo
                    client.calls.size shouldBe 1
                }
            }

            When("썸네일을 채우면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        service.fill(link.id!!)
                    }

                Then("제목·요약·메모·태그·폴더·상태는 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.title shouldBe link.title
                    actual.summary shouldBe link.summary
                    actual.memo shouldBe link.memo
                    actual.tags shouldBe link.tags
                    actual.folderId shouldBe link.folderId
                    actual.status shouldBe link.status
                }
            }

            When("호출하는 동안 사용자가 썸네일을 넣었으면") {
                val userThumbnailUrl = RandomFixture.randomUrl()
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = {
                            linkRepository.updatePreview(
                                link.fillPreview(thumbnailUrl = userThumbnailUrl, faviconUrl = null, isVideo = false),
                            )
                        }
                        service.fill(link.id!!)
                    }

                Then("사용자 값이 덮어써지지 않는다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe userThumbnailUrl
                    actual.faviconUrl shouldBe null
                }
            }

            When("호출하는 동안 링크가 지워졌으면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        client.onExtract = { linkRepository.deleteById(link.id!!) }
                        service.fill(link.id!!)
                    }

                Then("예외 없이 끝나고 링크는 없다") {
                    shouldNotThrowAny { fill() }

                    linkRepository.findById(link.id!!) shouldBe null
                }
            }

            When("같은 링크를 두 번 채우면") {
                val firstKey = LinkPreviewFixture.key(kind = "preview")
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = firstKey))
                        service.fill(link.id!!)
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        service.fill(link.id!!)
                    }

                Then("첫 번째 값이 유지되고 Lambda는 한 번만 불린다") {
                    fill()

                    linkRepository.findById(link.id!!)?.thumbnailUrl shouldBe "$PUBLIC_BASE_URL/$firstKey"
                    client.calls.size shouldBe 1
                }
            }
        }

        Given("썸네일만 있는 링크") {
            lateinit var link: Link

            beforeEach {
                client.reset()
                link = insertLinkOf(thumbnailUrl = RandomFixture.randomUrl())
            }

            When("채우기를 실행하면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(faviconKey = LinkPreviewFixture.key(kind = "favicon")))
                        service.fill(link.id!!)
                    }

                Then("Lambda를 부르지 않고 링크가 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe link.thumbnailUrl
                    actual.faviconUrl shouldBe null
                    actual.isVideo shouldBe link.isVideo
                    client.calls.size shouldBe 0
                }
            }
        }

        Given("파비콘만 있는 링크") {
            lateinit var link: Link

            beforeEach {
                client.reset()
                link = insertLinkOf(faviconUrl = RandomFixture.randomUrl())
            }

            When("채우기를 실행하면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        service.fill(link.id!!)
                    }

                Then("Lambda를 부르지 않고 링크가 그대로다") {
                    fill()

                    val actual = linkRepository.findById(link.id!!).shouldNotBeNull()
                    actual.thumbnailUrl shouldBe null
                    actual.faviconUrl shouldBe link.faviconUrl
                    actual.isVideo shouldBe link.isVideo
                    client.calls.size shouldBe 0
                }
            }
        }

        Given("존재하지 않는 링크 id") {
            beforeEach {
                client.reset()
            }

            When("채우기를 실행하면") {
                val fill =
                    suspend {
                        client.respond(LinkPreviewResult(thumbnailKey = LinkPreviewFixture.key(kind = "preview")))
                        service.fill(RandomFixture.randomId())
                    }

                Then("예외 없이 끝나고 Lambda를 부르지 않는다") {
                    shouldNotThrowAny { fill() }

                    client.calls.size shouldBe 0
                }
            }
        }
    })
