package com.qlink.folder.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.SecurityConfig
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.AcceptFolderInvitationRequest
import com.qlink.folder.dto.CreateFolderInvitationRequest
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Date
import kotlin.time.Clock

class AcceptFolderInvitationServiceTest :
    BaseServiceTest({
        val createFolderInvitationService = koinGet<CreateFolderInvitationService>()
        val acceptFolderInvitationService = koinGet<AcceptFolderInvitationService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val securityConfig = koinGet<SecurityConfig>()

        fun invitationOf(folderId: Long): String =
            JWT
                .create()
                .withSubject(folderId.toString())
                .sign(Algorithm.HMAC256(securityConfig.jwtSecret))

        fun expiredInvitationOf(folderId: Long): String =
            JWT
                .create()
                .withSubject(folderId.toString())
                .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
                .sign(Algorithm.HMAC256(securityConfig.jwtSecret))

        Given("공유 폴더 초대 수락 서비스 테스트") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var folder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                folder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = Clock.System.now(),
                        ),
                    )
            }

            When("유효한 초대를 수락하면") {
                val accept =
                    suspend {
                        val invitation =
                            createFolderInvitationService
                                .createInvitation(
                                    loginId = owner.id!!,
                                    folderId = folder.id!!,
                                    request = CreateFolderInvitationRequest(durationDays = 1),
                                ).invitation

                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = invitation),
                        )
                    }

                Then("멤버로 등록된다") {
                    val response = accept()
                    val actual = folderMemberRepository.findByFolderIdAndUserId(folder.id!!, member.id!!)

                    response.folderId shouldBe folder.id
                    actual.shouldNotBeNull()
                    actual.userName shouldBe member.nickname
                }
            }

            When("이미 참여 중인 초대를 수락하면") {
                val accept =
                    suspend {
                        val invitation = invitationOf(folder.id!!)

                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = invitation),
                        )
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = invitation),
                        )
                    }

                Then("멱등하게 성공한다") {
                    val response = accept()
                    val actual = folderMemberRepository.findByFolderIdAndUserId(folder.id!!, member.id!!)

                    response.folderId shouldBe folder.id
                    actual.shouldNotBeNull()
                }
            }

            When("로그인 사용자가 없으면") {
                val accept =
                    suspend {
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = RandomFixture.randomId(),
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = invitationOf(folder.id!!)),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
                        accept()
                    }
                }
            }

            When("토큰이 잘못되면") {
                val accept =
                    suspend {
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = "invalid"),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_INVITATION_INVALID.message) {
                        accept()
                    }
                }
            }

            When("토큰의 폴더와 요청 폴더가 다르면") {
                val accept =
                    suspend {
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = invitationOf(RandomFixture.randomId())),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_INVITATION_INVALID.message) {
                        accept()
                    }
                }
            }

            When("토큰이 만료되면") {
                val accept =
                    suspend {
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            request = AcceptFolderInvitationRequest(invitation = expiredInvitationOf(folder.id!!)),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_INVITATION_EXPIRED.message) {
                        accept()
                    }
                }
            }

            When("폴더가 없으면") {
                val missingFolderId = RandomFixture.randomId()
                val accept =
                    suspend {
                        acceptFolderInvitationService.acceptInvitation(
                            loginId = member.id!!,
                            folderId = missingFolderId,
                            request = AcceptFolderInvitationRequest(invitation = invitationOf(missingFolderId)),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_FOUND.message) {
                        accept()
                    }
                }
            }
        }
    })
