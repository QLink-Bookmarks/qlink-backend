package com.qlink.folderinvite.domain

import com.qlink.folderinvite.domain.FolderInvite
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class FolderInviteTest :
    StringSpec({
        "creates a folder invite domain model" {
            val now = Clock.System.now()
            val folderInvite =
                FolderInvite(
                    id = 1,
                    folderId = 2,
                    inviterId = 3,
                    token = "token",
                    expiresAt = now + 1.days,
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
            folderInvite.copy(expiresAt = now + 2.days) shouldNotBe folderInvite
            folderInvite.copy(acceptedAt = now) shouldNotBe folderInvite
            folderInvite.copy(createdAt = now + 1.seconds) shouldNotBe folderInvite
            folderInvite.copy(updatedAt = now + 2.seconds) shouldNotBe folderInvite
            folderInvite.equals("invite") shouldBe false
            folderInvite.hashCode() shouldBe folderInvite.copy().hashCode()
        }
    })
