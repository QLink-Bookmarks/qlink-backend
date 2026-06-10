package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class GetFolderMembersServiceTest :
    BaseServiceTest({
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val getFolderMembersService = koinGet<GetFolderMembersService>()

        Given("공유 폴더 멤버 조회 서비스 테스트") {
            lateinit var owner: User
            lateinit var member: User
            lateinit var latestMember: User
            lateinit var otherUser: User
            lateinit var folder: Folder

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
                latestMember = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = owner.id!!,
                            sharedAt = Clock.System.now(),
                        ),
                    )

                val joinedAt = Clock.System.now()
                folderMemberRepository.insertIfAbsent(
                    FolderMember.owner(
                        folderId = folder.id!!,
                        userId = owner.id!!,
                        userName = "stale-owner-name",
                        joinedAt = joinedAt,
                    ),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMember.member(
                        folderId = folder.id!!,
                        userId = member.id!!,
                        userName = "stale-member-name",
                        joinedAt = joinedAt + 1.seconds,
                    ),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMember.member(
                        folderId = folder.id!!,
                        userId = latestMember.id!!,
                        userName = "stale-latest-member-name",
                        joinedAt = joinedAt + 2.seconds,
                    ),
                )
            }

            When("공유 폴더의 멤버를 조회하면") {
                val get =
                    suspend {
                        getFolderMembersService.getFolderMembers(
                            loginId = member.id!!,
                            folderId = folder.id!!,
                        )
                    }

                Then("소유자 정보와 멤버 목록을 joinedAt 내림차순으로 반환한다") {
                    val response = get()

                    response.ownerId shouldBe owner.id
                    response.ownerNickname shouldBe owner.nickname
                    response.members.shouldHaveSize(3)
                    response.members.map { it.userId } shouldBe listOf(latestMember.id, member.id, owner.id)
                    response.members.map { it.role } shouldBe listOf("MEMBER", "MEMBER", "OWNER")
                    response.members.map { it.userNickname } shouldBe listOf(latestMember.nickname, member.nickname, owner.nickname)
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getFolderMembersService.getFolderMembers(
                            loginId = RandomFixture.randomId(),
                            folderId = folder.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_MEMBER_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }

            When("폴더가 없으면") {
                val get =
                    suspend {
                        getFolderMembersService.getFolderMembers(
                            loginId = member.id!!,
                            folderId = RandomFixture.randomId(),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_MEMBER_FOLDER_NOT_FOUND.message) {
                        get()
                    }
                }
            }

            When("폴더가 공유 폴더가 아니면") {
                lateinit var unsharedFolder: Folder

                beforeTest {
                    unsharedFolder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(
                                ownerId = owner.id!!,
                                sharedAt = null,
                            ),
                        )
                }

                val get =
                    suspend {
                        getFolderMembersService.getFolderMembers(
                            loginId = owner.id!!,
                            folderId = unsharedFolder.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_MEMBER_NOT_SHARED_FOLDER.message) {
                        get()
                    }
                }
            }

            When("로그인 사용자가 폴더 멤버가 아니면") {
                val get =
                    suspend {
                        getFolderMembersService.getFolderMembers(
                            loginId = otherUser.id!!,
                            folderId = folder.id!!,
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_MEMBER_ACCESS_DENIED.message) {
                        get()
                    }
                }
            }
        }
    })
