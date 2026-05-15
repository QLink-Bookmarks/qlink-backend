package com.qlink.foldermember.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FolderMemberTest :
    StringSpec({
        "creates a folder member domain model" {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val folderMember =
                FolderMember(
                    folderId = 1,
                    userId = 2,
                    role = "owner",
                    joinedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            folderMember.folderId shouldBe 1
            folderMember.role shouldBe "owner"
            folderMember.copy() shouldBe folderMember
            folderMember.copy(folderId = 2) shouldNotBe folderMember
            folderMember.copy(userId = 3) shouldNotBe folderMember
            folderMember.copy(role = "member") shouldNotBe folderMember
            folderMember.copy(joinedAt = now.plusSeconds(1)) shouldNotBe folderMember
            folderMember.copy(createdAt = now.plusSeconds(2)) shouldNotBe folderMember
            folderMember.copy(updatedAt = now.plusSeconds(3)) shouldNotBe folderMember
            folderMember.equals("member") shouldBe false
            folderMember.hashCode() shouldBe folderMember.copy().hashCode()
        }
    })
