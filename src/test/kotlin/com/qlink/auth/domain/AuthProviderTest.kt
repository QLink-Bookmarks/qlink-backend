package com.qlink.auth.domain

import com.qlink.auth.domain.AuthProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class AuthProviderTest :
    StringSpec({
        "creates an auth provider domain model" {
            val now = Clock.System.now()
            val authProvider =
                AuthProvider(
                    id = 1,
                    userId = 2,
                    providerType = AuthProviderType.KAKAO,
                    providerId = "google-user",
                    createdAt = now,
                    updatedAt = now,
                )

            authProvider.userId shouldBe 2
            authProvider.providerType shouldBe AuthProviderType.KAKAO
            authProvider.copy() shouldBe authProvider
            authProvider.copy(id = 2) shouldNotBe authProvider
            authProvider.copy(userId = 3) shouldNotBe authProvider
            authProvider.copy(providerId = "other") shouldNotBe authProvider
            authProvider.copy(createdAt = now + 1.seconds) shouldNotBe authProvider
            authProvider.copy(updatedAt = now + 2.seconds) shouldNotBe authProvider
            authProvider.equals("auth") shouldBe false
            authProvider.hashCode() shouldBe authProvider.copy().hashCode()
        }
    })
