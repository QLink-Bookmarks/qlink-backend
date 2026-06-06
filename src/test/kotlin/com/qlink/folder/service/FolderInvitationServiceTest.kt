package com.qlink.folder.service

import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.AcceptFolderInvitationRequest
import com.qlink.folder.dto.CreateFolderInvitationRequest
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class FolderInvitationServiceTest :
    BaseServiceTest({
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val createFolderInvitationService = koinGet<CreateFolderInvitationService>()
        val acceptFolderInvitationService = koinGet<AcceptFolderInvitationService>()

        Given("공유 폴더 초대 서비스 테스트") {
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

            When("공유 폴더 초대를 생성하고 수락하면") {
                val accept = suspend {
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
        }
    })
