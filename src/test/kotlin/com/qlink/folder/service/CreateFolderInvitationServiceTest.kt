package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.CreateFolderInvitationRequest
import com.qlink.folder.repository.FolderRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.time.Clock

class CreateFolderInvitationServiceTest :
    BaseServiceTest({
        val createFolderInvitationService = koinGet<CreateFolderInvitationService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()

        Given("공유 폴더 초대 생성 서비스 테스트") {
            lateinit var owner: User
            lateinit var otherUser: User
            lateinit var sharedFolder: Folder
            lateinit var privateFolder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                sharedFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = Clock.System.now(),
                        ),
                    )
                privateFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = null,
                        ),
                    )
            }

            When("권한 있는 사용자가 초대를 생성하면") {
                val create =
                    suspend {
                        createFolderInvitationService.createInvitation(
                            loginId = owner.id!!,
                            folderId = sharedFolder.id!!,
                            request = CreateFolderInvitationRequest(durationDays = 1),
                        )
                    }

                Then("초대 토큰을 반환한다") {
                    create().invitation.shouldNotBeBlank()
                }
            }

            When("로그인 사용자가 없으면") {
                val create =
                    suspend {
                        createFolderInvitationService.createInvitation(
                            loginId = RandomFixture.randomId(),
                            folderId = sharedFolder.id!!,
                            request = CreateFolderInvitationRequest(durationDays = 1),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("폴더가 없으면") {
                val create =
                    suspend {
                        createFolderInvitationService.createInvitation(
                            loginId = owner.id!!,
                            folderId = RandomFixture.randomId(),
                            request = CreateFolderInvitationRequest(durationDays = 1),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("폴더 소유자가 아니면") {
                val create =
                    suspend {
                        createFolderInvitationService.createInvitation(
                            loginId = otherUser.id!!,
                            folderId = sharedFolder.id!!,
                            request = CreateFolderInvitationRequest(durationDays = 1),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        create()
                    }
                }
            }

            When("공유 폴더가 아니면") {
                val create =
                    suspend {
                        createFolderInvitationService.createInvitation(
                            loginId = owner.id!!,
                            folderId = privateFolder.id!!,
                            request = CreateFolderInvitationRequest(durationDays = 1),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_SHARED.message) {
                        create()
                    }
                }
            }
        }
    })
