package com.qlink.folder.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FolderTest :
    StringSpec({
        "creates a folder domain model" {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val folder =
                Folder(
                    id = 1,
                    ownerId = 2,
                    name = "Work",
                    emoji = ":folder:",
                    sharedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            folder.ownerId shouldBe 2
            folder.name shouldBe "Work"
            folder.copy() shouldBe folder
            folder.copy(id = 2) shouldNotBe folder
            folder.copy(ownerId = 3) shouldNotBe folder
            folder.copy(name = "Home") shouldNotBe folder
            folder.copy(emoji = null) shouldNotBe folder
            folder.copy(sharedAt = null) shouldNotBe folder
            folder.copy(createdAt = now.plusSeconds(1)) shouldNotBe folder
            folder.copy(updatedAt = now.plusSeconds(2)) shouldNotBe folder
            folder.equals("folder") shouldBe false
            folder.hashCode() shouldBe folder.copy().hashCode()
        }
    })
