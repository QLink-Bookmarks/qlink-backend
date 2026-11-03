package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ImportSourceFolderTest :
    BehaviorSpec({
        Given("원본 폴더 항목") {
            val now = Clock.System.now()

            When("createdAt이 저장 시각보다 미래면") {
                val resolved = ImportFixture.createSourceFolder(createdAt = now + 1.days).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }
        }
    })
