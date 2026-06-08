package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class DeleteFolderMemberServiceTest :
    BaseServiceTest({
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val deleteFolderMemberService = koinGet<DeleteFolderMemberService>()

        Given("공유 폴더 멤버 삭제 서비스 테스트") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var otherUser: User
            lateinit var folder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = Clock.System.now(),
                        ),
                    )
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(
                        folderId = folder.id!!,
                        userId = member.id!!,
                        userName = member.nickname,
                    ),
                )
            }

            When("소유자가 멤버를 삭제하면") {
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = owner.id!!,
                            folderId = folder.id!!,
                            memberId = member.id!!,
                        )
                    }

                Then("멤버가 삭제된다") {
                    delete()

                    folderMemberRepository.findByFolderIdAndUserId(folder.id!!, member.id!!) shouldBe null
                }
            }

            When("멤버 본인이 자신을 삭제하면") {
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                            memberId = member.id!!,
                        )
                    }

                Then("멤버가 삭제된다") {
                    delete()

                    folderMemberRepository.findByFolderIdAndUserId(folder.id!!, member.id!!) shouldBe null
                }
            }

            When("삭제 대상 멤버가 이미 없으면") {
                val missingMemberId = otherUser.id!!
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = owner.id!!,
                            folderId = folder.id!!,
                            memberId = missingMemberId,
                        )
                    }

                Then("성공 응답처럼 처리한다") {
                    delete()

                    folderMemberRepository.findByFolderIdAndUserId(folder.id!!, missingMemberId) shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = RandomFixture.randomId(),
                            folderId = folder.id!!,
                            memberId = member.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }

            When("폴더가 없으면") {
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = owner.id!!,
                            folderId = RandomFixture.randomId(),
                            memberId = member.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }

            When("소유자도 삭제 대상 멤버 본인도 아니면") {
                val delete =
                    suspend {
                        deleteFolderMemberService.deleteMember(
                            loginId = otherUser.id!!,
                            folderId = folder.id!!,
                            memberId = member.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        delete()
                    }
                }
            }
        }
    })
