package com.qlink.bookmarkimport.domain

import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

class ImportPlanLinkTest :
    BehaviorSpec({
        Given("기존 폴더·새 폴더·미분류 계획 링크") {
            val existing = ImportFixture.createExistingPlanFolder()
            val new = ImportFixture.createNewPlanFolder()
            val links = listOf(existing, new, null).map { ImportFixture.createPlanLinkOf(folder = it) }
            val newFolderIdByName = mapOf(new.name to RandomFixture.randomId())

            When("저장된 폴더 id를 구하면") {
                val folderIds = links.map { it.savedFolderId(newFolderIdByName) }

                Then("기존 폴더 id, 저장된 새 폴더 id, 미분류 null이다") {
                    folderIds shouldContainExactly listOf(existing.folderId, newFolderIdByName.getValue(new.name), null)
                }
            }
        }
    })
