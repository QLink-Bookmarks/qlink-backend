package com.qlink.folder.domain.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec

class FolderDeleteTargetDomainServiceTest :
    BehaviorSpec({
        Given("이동 target과 호출자의 관계") {
            val callerId = RandomFixture.randomId()
            val ownerId = RandomFixture.randomId()
            val owned = FolderFixture.createValidUnsharedFolder(ownerId = callerId)
            val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId)
            val private = FolderFixture.createValidUnsharedFolder(ownerId = ownerId)
            val member = FolderMemberFixture.createMember(folderId = shared.id!!, userId = callerId)
            When("호출자가 target 소유자이면") {
                val validate = {
                    FolderDeleteTargetDomainService.validateWritable(target = owned, member = null, loginId = callerId)
                }
                Then("허용한다") { shouldNotThrowAny { validate() } }
            }
            When("공유 target의 현재 멤버이면") {
                val validate = {
                    FolderDeleteTargetDomainService.validateWritable(target = shared, member = member, loginId = callerId)
                }
                Then("허용한다") { shouldNotThrowAny { validate() } }
            }
            When("다른 사람의 개인 target이면") {
                val validate = {
                    FolderDeleteTargetDomainService.validateWritable(target = private, member = null, loginId = callerId)
                }
                Then("target 접근 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED.message) { validate() }
                }
            }
            When("공유 target의 멤버가 아니면") {
                val validate = {
                    FolderDeleteTargetDomainService.validateWritable(target = shared, member = null, loginId = callerId)
                }
                Then("target 접근 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED.message) { validate() }
                }
            }
            listOf(
                "다른 폴더" to
                    FolderMemberFixture.createMember(
                        folderId = RandomFixture.randomId(),
                        userId = callerId,
                    ),
                "다른 사용자" to
                    FolderMemberFixture.createMember(
                        folderId = shared.id!!,
                        userId = RandomFixture.randomId(),
                    ),
            ).forEach { (relationship, mismatchedMember) ->
                When("공유 target에 $relationship 의 member를 전달하면") {
                    val validate = {
                        FolderDeleteTargetDomainService.validateWritable(
                            target = shared,
                            member = mismatchedMember,
                            loginId = callerId,
                        )
                    }
                    Then("target 접근 오류다") {
                        shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED.message) { validate() }
                    }
                }
            }
            When("공유 해제 target에 stale member만 있으면") {
                val staleMember = FolderMemberFixture.createMember(folderId = private.id!!, userId = callerId)
                val validate = {
                    FolderDeleteTargetDomainService.validateWritable(target = private, member = staleMember, loginId = callerId)
                }
                Then("target 접근 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED.message) { validate() }
                }
            }
        }
    })
