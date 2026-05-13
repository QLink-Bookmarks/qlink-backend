package com.qlink.link.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class LinkTest :
  StringSpec({
    "creates a link domain model" {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val link = Link(
        id = 1,
        ownerId = 2,
        folderId = 3,
        url = "https://example.com",
        title = "Example",
        summary = "Summary",
        oneLiner = "One liner",
        tags = listOf("kotlin"),
        thumbnailUrl = "https://example.com/image.png",
        sourceType = "web",
        reminderAt = now.plusDays(1),
        createdAt = now,
        updatedAt = now,
      )

      link.url shouldBe "https://example.com"
      link.tags shouldBe listOf("kotlin")
      link.copy() shouldBe link
      link.copy(id = 2) shouldNotBe link
      link.copy(ownerId = 4) shouldNotBe link
      link.copy(folderId = 5) shouldNotBe link
      link.copy(url = "https://other.example.com") shouldNotBe link
      link.copy(title = "Other") shouldNotBe link
      link.copy(summary = null) shouldNotBe link
      link.copy(oneLiner = null) shouldNotBe link
      link.copy(tags = listOf("postgres")) shouldNotBe link
      link.copy(thumbnailUrl = null) shouldNotBe link
      link.copy(sourceType = "article") shouldNotBe link
      link.copy(reminderAt = null) shouldNotBe link
      link.copy(createdAt = now.plusSeconds(1)) shouldNotBe link
      link.copy(updatedAt = now.plusSeconds(2)) shouldNotBe link
      link.equals("link") shouldBe false
      link.hashCode() shouldBe link.copy().hashCode()
    }
  })
