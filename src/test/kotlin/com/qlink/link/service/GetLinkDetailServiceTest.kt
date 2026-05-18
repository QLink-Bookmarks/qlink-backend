package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
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

class GetLinkDetailServiceTest :
    BaseServiceTest({
        val getLinkDetailService = koinGet<GetLinkDetailService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()

        Given("링크 상세 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("폴더가 있는 본인 링크 상세 조회를") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            folderId = folder.id,
                        ),
                    )
                val expectedFolderId = folder.id
                val expectedFolderName = folder.name
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id!!)

                Then("성공한다") {
                    actual.id shouldBe link.id
                    actual.url shouldBe link.url
                    actual.title shouldBe link.title
                    actual.summary shouldBe link.summary
                    actual.tags shouldBe link.tags
                    actual.memo shouldBe link.memo
                    actual.sourceType shouldBe link.sourceType
                    actual.createdAt shouldBe link.createdAt
                    actual.folderId shouldBe expectedFolderId
                    actual.folderName shouldBe expectedFolderName
                }
            }

            When("폴더가 없는 본인 링크 상세 조회를") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                val actual = getLinkDetailService.getLinkDetail(user.id!!, link.id!!)

                Then("폴더 정보 없이 성공한다") {
                    actual.id shouldBe link.id
                    actual.folderId shouldBe null
                    actual.folderName shouldBe null
                }
            }

            When("없는 링크 상세 조회를") {
                val invalidLinkId = RandomFixture.randomId()
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, invalidLinkId)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        getDetail()
                    }
                }
            }

            When("다른 사용자의 링크 상세 조회를") {
                val link: Link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = otherUser.id!!,
                        ),
                    )
                val getDetail =
                    suspend {
                        getLinkDetailService.getLinkDetail(user.id!!, link.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        getDetail()
                    }
                }
            }
        }
    })
