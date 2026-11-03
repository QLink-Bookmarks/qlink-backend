package com.qlink.bookmarkimport.domain

import com.qlink.link.domain.SourceType
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class ImportPlanTest :
    BehaviorSpec({
        Given("기존 폴더·새 폴더·미분류 링크와 실패가 있는 가져오기 계획") {
            val ownerId = RandomFixture.randomId()
            val importId = RandomFixture.randomId()
            val now = Clock.System.now()
            val existing = FolderFixture.createFolderWith(ownerId = ownerId, name = "기존 ${RandomFixture.randomId()}")
            val existingSource = ImportFixture.createSourceFolder(folderId = existing.id)
            val newSource = ImportFixture.createSourceFolder(createdAt = RandomFixture.randomPastInstant())
            val links =
                listOf(
                    ImportFixture.createSourceLink(folderKey = existingSource.key),
                    ImportFixture.createSourceLink(folderKey = newSource.key, createdAt = RandomFixture.randomPastInstant()),
                    ImportFixture.createSourceLink(),
                    ImportFixture.createSourceLink(title = " "),
                )
            val plan =
                ImportFixture
                    .createSource(folders = listOf(existingSource, newSource), links = links)
                    .plan(ownerId, listOf(existing), emptyList(), now)
            val savedNewFolder = FolderFixture.createFolderWith(ownerId = ownerId, name = plan.newFolders.single().name)

            When("저장된 새 폴더로 링크를 만들면") {
                val created = plan.toLinks(listOf(savedNewFolder))

                Then("기존 폴더·저장된 새 폴더·미분류에 가져온 링크로 만들어진다") {
                    created.map { it.folderId } shouldContainExactly listOf(existing.id, savedNewFolder.id, null)
                    created.map { it.url } shouldContainExactly links.take(3).map { it.url }
                    created.map { it.createdAt } shouldContainExactly plan.links.map { it.createdAt }
                    created.forEach { it.sourceType shouldBe SourceType.IMPORT }
                    created.forEach { it.ownerId shouldBe ownerId }
                }
            }

            When("가져오기 폴더 행으로 바꾸면") {
                val rows = plan.toImportFolders(importId, listOf(savedNewFolder))

                Then("대상 폴더마다 종류와 저장된 폴더 id가 담긴다") {
                    rows.map { it.folderId } shouldContainExactly listOf(existing.id, savedNewFolder.id)
                    rows.map { it.kind } shouldContainExactly listOf(ImportFolderKind.EXISTING, ImportFolderKind.NEW)
                    rows.map { it.name } shouldContainExactly listOf(existing.name, savedNewFolder.name)
                    rows.forEach { it.importId shouldBe importId }
                }
            }

            When("저장된 링크를 가져오기 링크 행으로 바꾸면") {
                val savedLinks =
                    plan.toLinks(listOf(savedNewFolder)).map {
                        LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = it.folderId, url = it.url, title = it.title)
                    }
                val savedRows =
                    plan.toImportFolders(importId, listOf(savedNewFolder)).map {
                        ImportFixture.createImportFolderOf(importId = importId, folderId = it.folderId, kind = it.kind, name = it.name)
                    }
                val rows = plan.toImportLinks(importId, savedLinks, savedRows)

                Then("링크 id와 그 링크가 들어간 가져오기 폴더 행 id가 담기고 썸네일 작업은 PENDING이다") {
                    rows.map { it.linkId } shouldContainExactly savedLinks.map { it.id }
                    rows.map { it.importFolderId } shouldContainExactly listOf(savedRows[0].id, savedRows[1].id, null)
                    rows.map { it.title } shouldContainExactly savedLinks.map { it.title }
                    rows.forEach { it.thumbnailStatus shouldBe ThumbnailStatus.PENDING }
                }
            }

            When("실패 행으로 바꾸면") {
                val failures = plan.toImportFailures(importId)

                Then("실패한 링크의 제목·URL·사유가 담긴다") {
                    val failure = failures.single()
                    failure.importId shouldBe importId
                    failure.url shouldBe links.last().url
                    failure.title shouldBe links.last().title
                    failure.reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }
        }
    })
