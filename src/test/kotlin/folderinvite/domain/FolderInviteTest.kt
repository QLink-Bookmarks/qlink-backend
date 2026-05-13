package com.qlink.folderinvite.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FolderInviteTest :
  StringSpec({
    "creates a folder invite domain model" {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val folderInvite = FolderInvite(
        id = 1,
        folderId = 2,
        inviterId = 3,
        token = "token",
        expiresAt = now.plusDays(1),
        acceptedAt = null,
        createdAt = now,
        updatedAt = now,
      )

      folderInvite.folderId shouldBe 2
      folderInvite.token shouldBe "token"
      folderInvite.copy() shouldBe folderInvite
      folderInvite.copy(id = 2) shouldNotBe folderInvite
      folderInvite.copy(folderId = 4) shouldNotBe folderInvite
      folderInvite.copy(inviterId = 5) shouldNotBe folderInvite
      folderInvite.copy(token = "other") shouldNotBe folderInvite
      folderInvite.copy(expiresAt = now.plusDays(2)) shouldNotBe folderInvite
      folderInvite.copy(acceptedAt = now) shouldNotBe folderInvite
      folderInvite.copy(createdAt = now.plusSeconds(1)) shouldNotBe folderInvite
      folderInvite.copy(updatedAt = now.plusSeconds(2)) shouldNotBe folderInvite
      folderInvite.equals("invite") shouldBe false
      folderInvite.hashCode() shouldBe folderInvite.copy().hashCode()
    }
  })
