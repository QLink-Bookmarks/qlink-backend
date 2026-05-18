package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec

class FolderTest :
    BehaviorSpec({
        Given("폴더 생성 테스트") {
            // TODO: 추후 추가 필요
        }

        Given("소유자 검증 테스트") {
            val ownerId = RandomFixture.randomId()
            val folder = FolderFixture.createValidUnsharedFolder(ownerId)

            When("소유자가 맞으면") {
                val validate = {
                    folder.validateOwner(ownerId)
                }

                Then("검증을 성공한다") {
                    shouldNotThrowAny {
                        validate()
                    }
                }
            }

            When("소유자가 다르면") {
                val differentUserId = RandomFixture.randomId()
                val validate = {
                    folder.validateOwner(differentUserId)
                }

                Then("검증을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        validate()
                    }
                }
            }
        }
    })
