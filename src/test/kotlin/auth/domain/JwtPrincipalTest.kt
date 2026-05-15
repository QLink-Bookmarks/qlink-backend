package com.qlink.auth.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class JwtPrincipalTest :
    BehaviorSpec({
        Given("JWT 생성 테스트") {
            val userId = 1L
            val role = Role.NORMAL

            When("JWT Principal 생성을") {
                val principal = JwtPrincipal(userId, role)

                Then("성공한다") {
                    principal.userId shouldBe userId
                    principal.role shouldBe role
                }
            }
        }

        Given("기본 Principal 테스트") {
            val principal = JwtPrincipal(1L, role = Role.NORMAL)
        }
    })
