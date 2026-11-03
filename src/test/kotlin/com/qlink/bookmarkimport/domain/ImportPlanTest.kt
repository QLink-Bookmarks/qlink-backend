package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ImportPlanTest :
    BehaviorSpec({
        Given("기존 폴더·새 폴더·미분류 링크와 실패가 있는 가져오기 계획") {
            val importId = RandomFixture.randomId()
            val existing = ImportFixture.createExistingPlanFolder()
            val new = ImportFixture.createNewPlanFolder()
            val links = listOf(existing, new, null).map { ImportFixture.createPlanLinkOf(folder = it) }
            val failure = ImportFixture.createPlanFailureOf(reason = ImportFailureReason.TITLE_INVALID)
            val plan = ImportFixture.createPlanOf(folders = listOf(existing, new), links = links, failures = listOf(failure))
            val newFolderIdByName = mapOf(new.name to RandomFixture.randomId())

            When("가져오기 폴더 행으로 바꾸면") {
                val rows = plan.toImportFolders(importId = importId, newFolderIdByName = newFolderIdByName)

                Then("대상 폴더마다 종류와 저장된 폴더 id가 담긴다") {
                    rows.map { it.folderId } shouldContainExactly listOf(existing.folderId, newFolderIdByName.getValue(new.name))
                    rows.map { it.kind } shouldContainExactly listOf(ImportFolderKind.EXISTING, ImportFolderKind.NEW)
                    rows.map { it.name } shouldContainExactly listOf(existing.name, new.name)
                    rows.forEach { it.importId shouldBe importId }
                }
            }

            When("실패 행으로 바꾸면") {
                val failures = plan.toImportFailures(importId)

                Then("실패한 링크의 제목·URL·사유가 담긴다") {
                    val actual = failures.single()
                    actual.importId shouldBe importId
                    actual.url shouldBe failure.url
                    actual.title shouldBe failure.title
                    actual.reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("개수를 구하면") {
                val counts = listOf(plan.linkCount, plan.folderCount, plan.failedCount)

                Then("만들 링크·새 폴더·실패 수다") {
                    counts shouldContainExactly listOf(links.size, 1, 1)
                    plan.newFolders shouldContainExactly listOf(new)
                }
            }
        }
    })
