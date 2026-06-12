package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DeleteAccountServiceTest :
    BaseServiceTest({
        val service = koinGet<DeleteAccountService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()

        Given("회원탈퇴 요청이 주어졌을 때") {
            When("개인 폴더만 가진 사용자가 탈퇴하면") {
                val user = userRepository.insert(UserFixture.createRandomValidUser())
                val userId = requireNotNull(user.id)
                folderRepository.insert(FolderFixture.createValidUnsharedFolder(userId))

                val withdraw =
                    suspend {
                        service.deleteAccount(userId)
                    }

                Then("사용자가 삭제된다") {
                    shouldNotThrow<BusinessException> { withdraw() }
                    userRepository.findById(userId).shouldBeNull()
                }
            }

            When("다른 멤버가 있는 소유 공유 폴더가 있으면") {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                val ownerId = requireNotNull(owner.id)
                val next = userRepository.insert(UserFixture.createRandomValidUser())
                val nextId = requireNotNull(next.id)
                val late = userRepository.insert(UserFixture.createRandomValidUser())
                val lateId = requireNotNull(late.id)

                val now = Clock.System.now()
                val folder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(ownerId = ownerId, sharedAt = now),
                    )
                val folderId = requireNotNull(folder.id)
                folderMemberRepository.insertIfAbsent(
                    FolderMember.owner(folderId, ownerId, owner.nickname, now - 2.minutes),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMember.member(folderId, nextId, next.nickname, now - 1.minutes),
                )
                folderMemberRepository.insertIfAbsent(
                    FolderMember.member(folderId, lateId, late.nickname, now),
                )

                val withdraw =
                    suspend {
                        service.deleteAccount(ownerId)
                    }

                Then("owner 다음으로 join한 멤버에게 폴더가 위임된다") {
                    shouldNotThrow<BusinessException> { withdraw() }

                    val delegated = folderRepository.findById(folderId)
                    delegated.shouldNotBeNull()
                    delegated.ownerId shouldBe nextId

                    folderMemberRepository.findByFolderIdAndUserId(folderId, nextId)!!.role shouldBe MemberRole.OWNER
                    folderMemberRepository.findByFolderIdAndUserId(folderId, ownerId).shouldBeNull()
                    userRepository.findById(ownerId).shouldBeNull()
                }
            }

            When("본인만 멤버인 소유 공유 폴더가 있으면") {
                val owner = userRepository.insert(UserFixture.createRandomValidUser())
                val ownerId = requireNotNull(owner.id)
                val now = Clock.System.now()
                val folder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(ownerId = ownerId, sharedAt = now),
                    )
                val folderId = requireNotNull(folder.id)
                folderMemberRepository.insertIfAbsent(
                    FolderMember.owner(folderId, ownerId, owner.nickname, now),
                )

                val withdraw =
                    suspend {
                        service.deleteAccount(ownerId)
                    }

                Then("공유 폴더와 멤버가 삭제된다") {
                    shouldNotThrow<BusinessException> { withdraw() }

                    folderRepository.findById(folderId).shouldBeNull()
                    folderMemberRepository.findByFolderIdAndUserId(folderId, ownerId).shouldBeNull()
                    userRepository.findById(ownerId).shouldBeNull()
                }
            }

            When("로그인 사용자가 없으면") {
                val withdraw =
                    suspend {
                        service.deleteAccount(RandomFixture.randomId())
                    }

                Then("사용자 없음 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        withdraw()
                    }
                }
            }
        }
    })
