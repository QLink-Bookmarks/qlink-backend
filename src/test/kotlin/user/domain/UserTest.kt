package com.qlink.user.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UserTest :
    StringSpec({
        "creates a user domain model" {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val user =
                User(
                    id = 1,
                    displayName = "Q",
                    avatarUrl = null,
                    avatarEmoji = ":link:",
                    createdAt = now,
                    updatedAt = now,
                )

            user.id shouldBe 1
            user.displayName shouldBe "Q"
            user.copy() shouldBe user
            user.copy(id = 2) shouldNotBe user
            user.copy(displayName = "Other") shouldNotBe user
            user.copy(avatarUrl = "https://example.com/avatar.png") shouldNotBe user
            user.copy(avatarEmoji = null) shouldNotBe user
            user.copy(createdAt = now.plusSeconds(1)) shouldNotBe user
            user.copy(updatedAt = now.plusSeconds(2)) shouldNotBe user
            user.equals("user") shouldBe false
            user.hashCode() shouldBe user.copy().hashCode()
        }
    })
