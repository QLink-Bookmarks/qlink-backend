package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec

class FolderDeleteOptionTest :
    BehaviorSpec({
        Given("삭제 옵션 문자열") {
            When("알 수 없는 옵션을 파싱하면") {
                val parse = { FolderDeleteOption.from(RandomFixture.randomSentenceWithMax(30)) }
                Then("잘못된 요청 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) { parse() }
                }
            }
        }
    })
