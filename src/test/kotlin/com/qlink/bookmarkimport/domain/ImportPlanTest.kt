package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

class ImportPlanTest :
    BehaviorSpec({
        Given("기존 폴더·새 폴더·미분류 링크와 실패가 있는 가져오기 계획") {
            val existing = ImportFixture.createExistingPlanFolder()
            val new = ImportFixture.createNewPlanFolder()
            val links = listOf(existing, new, null).map { ImportFixture.createPlanLinkOf(folder = it) }
            val failure = ImportFixture.createPlanFailureOf(reason = ImportFailureReason.TITLE_INVALID)
            val plan = ImportFixture.createPlanOf(folders = listOf(existing, new), links = links, failures = listOf(failure))

            When("개수를 구하면") {
                val counts = listOf(plan.linkCount, plan.folderCount, plan.failedCount)

                Then("만들 링크·새 폴더·실패 수다") {
                    counts shouldContainExactly listOf(links.size, 1, 1)
                    plan.newFolders shouldContainExactly listOf(new)
                }
            }
        }
    })
