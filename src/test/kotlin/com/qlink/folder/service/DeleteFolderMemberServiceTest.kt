package com.qlink.folder.service

import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
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
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(
                        folderId = folder.id!!,
                        userId = member.id!!,
                        userName = member.nickname,
                    ),
                )
            }

            When("소유자가 멤버를 삭제하면") {
                val delete = suspend {
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
        }
    })
