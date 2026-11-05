package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FolderDeletionOptionsTest :
    BehaviorSpec({
        Given("nullable target과 삭제 옵션") {
            listOf(
                null to FolderDeleteOption.NULL,
                "nUlL" to FolderDeleteOption.NULL,
                "cAsCaDe" to FolderDeleteOption.CASCADE,
            ).forEach { (raw, expected) ->
                When("$raw 옵션에 target을 생략하면") {
                    val options = FolderDeletionOptions.from(onDelete = raw, targetFolderId = null)
                    Then("파싱 결과와 target 부재를 유지한다") {
                        options.option shouldBe expected
                        options.targetFolderId shouldBe null
                    }
                }
            }
            listOf(RandomFixture.randomId(), 0L, -RandomFixture.randomId()).forEach { targetId ->
                When("대소문자가 섞인 move에 target $targetId 를 제공하면") {
                    val options = FolderDeletionOptions.from(onDelete = "mOvE", targetFolderId = targetId)
                    Then("존재 검사 없이 target을 보존한다") {
                        options.option shouldBe FolderDeleteOption.MOVE
                        options.targetFolderId shouldBe targetId
                    }
                }
            }
            When("move에 target을 생략하면") {
                val parse = { FolderDeletionOptions.from(onDelete = "move", targetFolderId = null) }
                Then("필수 target 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_REQUIRED.message) { parse() }
                }
            }
            listOf(null, "null", "cascade").forEach { raw ->
                When("$raw 옵션에 target을 제공하면") {
                    val parse = { FolderDeletionOptions.from(onDelete = raw, targetFolderId = RandomFixture.randomId()) }
                    Then("잘못된 요청 오류다") {
                        shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) { parse() }
                    }
                }
            }
        }
    })
