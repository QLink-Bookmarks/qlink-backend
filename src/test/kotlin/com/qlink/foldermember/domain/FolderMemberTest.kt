package com.qlink.foldermember.domain

import com.qlink.foldermember.domain.FolderMember
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class FolderMemberTest :
    StringSpec({
        "creates a folder member domain model" {
            val now = Clock.System.now()
            val folderMember =
                FolderMember(
                    folderId = 1,
                    userId = 2,
                    userName = "tester",
                    role = "owner",
                    joinedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            folderMember.folderId shouldBe 1
            folderMember.userName shouldBe "tester"
            folderMember.role shouldBe "owner"
            folderMember.copy() shouldBe folderMember
            folderMember.copy(folderId = 2) shouldNotBe folderMember
            folderMember.copy(userId = 3) shouldNotBe folderMember
            folderMember.copy(userName = "other") shouldNotBe folderMember
            folderMember.copy(role = "member") shouldNotBe folderMember
            folderMember.copy(joinedAt = now + 1.seconds) shouldNotBe folderMember
            folderMember.copy(createdAt = now + 2.seconds) shouldNotBe folderMember
            folderMember.copy(updatedAt = now + 3.seconds) shouldNotBe folderMember
            folderMember.equals("member") shouldBe false
            folderMember.hashCode() shouldBe folderMember.copy().hashCode()
        }
    })
