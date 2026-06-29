package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.link.dto.CopyLinkRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock

class CopyLinkServiceTest :
    BaseServiceTest({
        val copyLinkService = koinGet<CopyLinkService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val linkRepository = koinGet<LinkRepository>()

        Given("공유 폴더 링크 복사 서비스 테스트") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var otherUser: User
            lateinit var sharedFolder: Folder
            lateinit var personalFolder: Folder
            lateinit var sharedLink: Link

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())

                sharedFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = Clock.System.now(),
                        ),
                    )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createOwnerMember(folderId = sharedFolder.id!!, userId = owner.id!!),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = sharedFolder.id!!, userId = member.id!!),
                )

                personalFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(member.id!!))

                sharedLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = owner.id!!,
                            folderId = sharedFolder.id!!,
                            memo = RandomFixture.randomSentenceWithMax(1000),
                        ),
                    )
            }

            When("공유 폴더의 링크를 개인 폴더에 복사하면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("로그인 사용자 소유로 개인 폴더에 복사된다") {
                    val response = copy()
                    val copied = linkRepository.findById(response.id)

                    copied shouldNotBe null
                    copied!!.id shouldNotBe sharedLink.id
                    copied.ownerId shouldBe member.id
                    copied.folderId shouldBe personalFolder.id
                    copied.url shouldBe sharedLink.url
                    copied.title shouldBe sharedLink.title
                    copied.summary shouldBe sharedLink.summary
                    copied.tags shouldBe sharedLink.tags
                    copied.thumbnailUrl shouldBe sharedLink.thumbnailUrl
                    copied.sourceType shouldBe SourceType.COPY
                    copied.memo shouldBe null
                    copied.favoriteAt shouldBe null
                }
            }

            When("개인 폴더를 지정하지 않고 복사하면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = null),
                        )
                    }

                Then("미분류(폴더 없음) 상태로 본인 소유 복사된다") {
                    val response = copy()
                    val copied = linkRepository.findById(response.id)

                    copied shouldNotBe null
                    copied!!.id shouldNotBe sharedLink.id
                    copied.ownerId shouldBe member.id
                    copied.folderId shouldBe null
                    copied.url shouldBe sharedLink.url
                    copied.sourceType shouldBe SourceType.COPY
                }
            }

            When("로그인 사용자가 없으면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = RandomFixture.randomId(),
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        copy()
                    }
                }
            }

            When("공유 폴더가 없으면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = RandomFixture.randomId(), toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_SHARE_FOLDER_NOT_FOUND.message) {
                        copy()
                    }
                }
            }

            When("공유 폴더가 공유 상태가 아니면") {
                lateinit var unsharedFolder: Folder

                beforeTest {
                    unsharedFolder = folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = null))
                }

                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = unsharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_COPY_NOT_SHARED_FOLDER.message) {
                        copy()
                    }
                }
            }

            When("로그인 사용자가 공유 폴더 멤버가 아니면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = otherUser.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED.message) {
                        copy()
                    }
                }
            }

            When("개인 폴더가 없으면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = RandomFixture.randomId()),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_TARGET_FOLDER_NOT_FOUND.message) {
                        copy()
                    }
                }
            }

            When("개인 폴더가 본인 소유가 아니면") {
                lateinit var othersFolder: Folder

                beforeTest {
                    othersFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
                }

                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = sharedLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = othersFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        copy()
                    }
                }
            }

            When("링크가 없으면") {
                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = RandomFixture.randomId(),
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        copy()
                    }
                }
            }

            When("링크의 폴더가 없으면") {
                lateinit var folderlessLink: Link

                beforeTest {
                    folderlessLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = null))
                }

                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = folderlessLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_COPY_LINK_FOLDER_NOT_FOUND.message) {
                        copy()
                    }
                }
            }

            When("링크의 폴더가 요청한 공유 폴더가 아니면") {
                lateinit var otherSharedFolder: Folder
                lateinit var otherFolderLink: Link

                beforeTest {
                    otherSharedFolder =
                        folderRepository.insert(FolderFixture.createFolderWith(ownerId = owner.id!!, sharedAt = Clock.System.now()))
                    otherFolderLink =
                        linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = owner.id!!, folderId = otherSharedFolder.id!!))
                }

                val copy =
                    suspend {
                        copyLinkService.copyLink(
                            loginId = member.id!!,
                            linkId = otherFolderLink.id!!,
                            request = CopyLinkRequest(fromFolderId = sharedFolder.id!!, toFolderId = personalFolder.id!!),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_COPY_FOLDER_MISMATCH.message) {
                        copy()
                    }
                }
            }
        }
    })
