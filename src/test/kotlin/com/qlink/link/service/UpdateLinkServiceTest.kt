package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.dto.UpdateLinkRequest
import com.qlink.link.dto.UpdateLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UpdateLinkServiceTest :
    BaseServiceTest({
        val updateLinkService = koinGet<UpdateLinkService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()

        Given("링크 수정 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder
            lateinit var otherFolder: Folder
            lateinit var link: Link
            lateinit var otherUserLink: Link
            lateinit var linkWithoutRequestedFolder: Link
            lateinit var linkWithOtherUserFolderRequest: Link
            lateinit var updateRequest: UpdateLinkRequest
            lateinit var otherUserLinkUpdateRequest: UpdateLinkRequest
            lateinit var missingFolderRequest: UpdateLinkRequest
            lateinit var otherUserFolderRequest: UpdateLinkRequest

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                otherFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
                link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                otherUserLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = otherUser.id!!,
                        ),
                    )
                linkWithoutRequestedFolder =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                linkWithOtherUserFolderRequest =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                updateRequest = LinkFixture.createValidUpdateLinkRequest(folderId = folder.id)
                otherUserLinkUpdateRequest = LinkFixture.createValidUpdateLinkRequest()
                missingFolderRequest = LinkFixture.createValidUpdateLinkRequest(folderId = RandomFixture.randomId())
                otherUserFolderRequest = LinkFixture.createValidUpdateLinkRequest(folderId = otherFolder.id)
            }

            When("본인 링크 수정을") {
                val update =
                    suspend {
                        val response = updateLinkService.updateLink(user.id!!, link.id!!, updateRequest)

                        UpdateResult(
                            link = link,
                            request = updateRequest,
                            response = response,
                        )
                    }

                Then("성공한다") {
                    val (link, request, response) = update()
                    val actual = linkRepository.findById(link.id!!)
                    val expectedTags = request.tags.distinct()

                    response.folderId shouldBe request.folderId
                    response.url shouldBe request.url
                    response.title shouldBe request.title
                    response.summary shouldBe request.summary
                    response.memo shouldBe request.memo
                    response.tags shouldBe expectedTags
                    response.thumbnailUrl shouldBe request.thumbnailUrl
                    response.sourceType shouldBe request.sourceType
                    response.isFavorite shouldBe false

                    actual shouldNotBe null
                    actual!!.folderId shouldBe request.folderId
                    actual.url shouldBe request.url
                    actual.title shouldBe request.title
                    actual.summary shouldBe request.summary
                    actual.memo shouldBe request.memo
                    actual.tags shouldBe expectedTags
                    actual.thumbnailUrl shouldBe request.thumbnailUrl
                    actual.sourceType shouldBe request.sourceType
                    actual.favoriteAt shouldBe null
                    actual.updatedAt shouldNotBe link.updatedAt
                }
            }

            When("isFavorite=true로 바로가기가 없는 링크를 수정하면") {
                val update =
                    suspend {
                        updateLinkService.updateLink(
                            user.id!!,
                            link.id!!,
                            LinkFixture.createValidUpdateLinkRequest(folderId = folder.id, isFavorite = true),
                        )
                    }

                Then("favoriteAt가 추가된다") {
                    val response = update()
                    val actual = linkRepository.findById(link.id!!)

                    response.isFavorite shouldBe true
                    actual!!.favoriteAt shouldNotBe null
                }
            }

            When("isFavorite=true로 이미 바로가기인 링크를 수정하면") {
                lateinit var favoritedLink: Link

                beforeTest {
                    favoritedLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                favoriteAt = RandomFixture.randomPastInstant(),
                            ),
                        )
                }

                Then("기존 favoriteAt가 유지된다") {
                    val response =
                        updateLinkService.updateLink(
                            user.id!!,
                            favoritedLink.id!!,
                            LinkFixture.createValidUpdateLinkRequest(isFavorite = true),
                        )
                    val actual = linkRepository.findById(favoritedLink.id!!)

                    response.isFavorite shouldBe true
                    actual!!.favoriteAt shouldBe favoritedLink.favoriteAt
                }
            }

            When("isFavorite=false로 바로가기인 링크를 수정하면") {
                lateinit var favoritedLink: Link

                beforeTest {
                    favoritedLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = user.id!!,
                                favoriteAt = RandomFixture.randomPastInstant(),
                            ),
                        )
                }

                Then("favoriteAt가 제거된다") {
                    val response =
                        updateLinkService.updateLink(
                            user.id!!,
                            favoritedLink.id!!,
                            LinkFixture.createValidUpdateLinkRequest(isFavorite = false),
                        )
                    val actual = linkRepository.findById(favoritedLink.id!!)

                    response.isFavorite shouldBe false
                    actual!!.favoriteAt shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val request = LinkFixture.createValidUpdateLinkRequest()
                val update =
                    suspend {
                        updateLinkService.updateLink(invalidUserId, RandomFixture.randomId(), request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("없는 링크 수정을") {
                val invalidLinkId = RandomFixture.randomId()
                val request = LinkFixture.createValidUpdateLinkRequest()
                val update =
                    suspend {
                        updateLinkService.updateLink(user.id!!, invalidLinkId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("다른 사용자의 링크 수정을") {
                val update =
                    suspend {
                        updateLinkService.updateLink(user.id!!, otherUserLink.id!!, otherUserLinkUpdateRequest)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }

            When("요청 폴더가 없으면") {
                val update =
                    suspend {
                        updateLinkService.updateLink(user.id!!, linkWithoutRequestedFolder.id!!, missingFolderRequest)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FOLDER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("요청 폴더가 다른 사용자 소유이면") {
                val update =
                    suspend {
                        updateLinkService.updateLink(user.id!!, linkWithOtherUserFolderRequest.id!!, otherUserFolderRequest)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }
        }
    })

private data class UpdateResult(
    val link: Link,
    val request: UpdateLinkRequest,
    val response: UpdateLinkResponse,
)
