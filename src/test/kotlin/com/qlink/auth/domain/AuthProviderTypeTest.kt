package com.qlink.auth.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AuthProviderTypeTest :
    BehaviorSpec({
        Given("인증 제공자 요청명이 주어졌을 때") {
            When("대소문자가 섞인 지원 제공자명이면") {
                val providerType = AuthProviderType.fromRequestName("kaKao")

                Then("지원 제공자로 변환한다") {
                    providerType shouldBe AuthProviderType.KAKAO
                }
            }

            When("지원하지 않는 제공자명이면") {
                val parse = {
                    AuthProviderType.fromRequestName("unknown")
                }

                Then("비즈니스 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED.message) {
                        parse()
                    }
                }
            }
        }
    })
