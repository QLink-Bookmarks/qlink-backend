package com.qlink.auth.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuthProviderTest :
    StringSpec({
        "creates an auth provider domain model" {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val authProvider =
                AuthProvider(
                    id = 1,
                    userId = 2,
                    providerType = "google",
                    providerId = "google-user",
                    createdAt = now,
                    updatedAt = now,
                )

            authProvider.userId shouldBe 2
            authProvider.providerType shouldBe "google"
            authProvider.copy() shouldBe authProvider
            authProvider.copy(id = 2) shouldNotBe authProvider
            authProvider.copy(userId = 3) shouldNotBe authProvider
            authProvider.copy(providerType = "kakao") shouldNotBe authProvider
            authProvider.copy(providerId = "other") shouldNotBe authProvider
            authProvider.copy(createdAt = now.plusSeconds(1)) shouldNotBe authProvider
            authProvider.copy(updatedAt = now.plusSeconds(2)) shouldNotBe authProvider
            authProvider.equals("auth") shouldBe false
            authProvider.hashCode() shouldBe authProvider.copy().hashCode()
        }
    })
