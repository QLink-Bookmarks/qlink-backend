package com.qlink.auth.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

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

        Given("JWT Principal 직렬화 테스트") {
            val principal = JwtPrincipal(userId = 1L, role = Role.NORMAL)

            When("JSON으로 직렬화하면") {
                val json = Json.encodeToString(principal)

                Then("필드가 포함된다") {
                    json shouldBe """{"userId":1,"role":"NORMAL"}"""
                }
            }
        }

        Given("JWT Principal 역직렬화 테스트") {
            val json = """{"userId":1,"role":"NORMAL"}"""

            When("JSON에서 역직렬화하면") {
                val principal = Json.decodeFromString<JwtPrincipal>(json)

                Then("Principal이 복원된다") {
                    principal shouldBe JwtPrincipal(userId = 1L, role = Role.NORMAL)
                }
            }
        }
    })
