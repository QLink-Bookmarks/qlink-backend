package com.qlink.folder.domain.service

import com.qlink.folder.domain.FolderDeletionOptions
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FolderDeletionDomainServiceTest :
    BehaviorSpec({
        Given("호출자와 타인의 링크를 가진 source") {
            val callerId = RandomFixture.randomId()
            val otherId = RandomFixture.randomId()
            val private = FolderFixture.createValidUnsharedFolder(ownerId = callerId)
            val shared = FolderFixture.createSharedFolderOf(ownerId = callerId)
            val mine = List(2) { LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = private.id) }
            val others = List(2) { LinkFixture.createRandomLinkOf(ownerId = otherId, folderId = private.id) }
            val all = mine + others
            When("개인 source의 move를 계획하면") {
                val plan =
                    FolderDeletionDomainService.plan(
                        source = private,
                        links = all,
                        callerId = callerId,
                        options = FolderDeletionOptions.from(onDelete = "move", targetFolderId = RandomFixture.randomId()),
                    )
                Then("내 링크만 이동하고 나머지는 detach한다") {
                    plan.moveLinkIds shouldBe mine.map { it.id }
                    plan.detachLinkIds shouldBe others.map { it.id }
                    plan.deleteLinkIds shouldBe emptyList()
                }
            }
            When("개인 source의 cascade를 계획하면") {
                val plan =
                    FolderDeletionDomainService.plan(
                        source = private,
                        links = all,
                        callerId = callerId,
                        options = FolderDeletionOptions.from(onDelete = "cascade", targetFolderId = null),
                    )
                Then("내 링크만 삭제하고 나머지는 detach한다") {
                    plan.deleteLinkIds shouldBe mine.map { it.id }
                    plan.detachLinkIds shouldBe others.map { it.id }
                    plan.moveLinkIds shouldBe emptyList()
                }
            }
            When("공유 source의 cascade를 계획하면") {
                val links = all.map { LinkFixture.createRandomLinkOf(ownerId = it.ownerId, folderId = shared.id) }
                val plan =
                    FolderDeletionDomainService.plan(
                        source = shared,
                        links = links,
                        callerId = callerId,
                        options = FolderDeletionOptions.from(onDelete = "cascade", targetFolderId = null),
                    )
                Then("모두 삭제한다") {
                    plan.deleteLinkIds shouldBe links.map { it.id }
                    plan.moveLinkIds shouldBe emptyList()
                    plan.detachLinkIds shouldBe emptyList()
                }
            }
            listOf(private, shared).forEach { source ->
                When("shared=${source.isShared} source의 null을 계획하면") {
                    val links = all.map { LinkFixture.createRandomLinkOf(ownerId = it.ownerId, folderId = source.id) }
                    val plan =
                        FolderDeletionDomainService.plan(
                            source = source,
                            links = links,
                            callerId = callerId,
                            options = FolderDeletionOptions.from(onDelete = null, targetFolderId = null),
                        )
                    Then("모두 detach한다") {
                        plan.detachLinkIds shouldBe links.map { it.id }
                        plan.deleteLinkIds shouldBe emptyList()
                        plan.moveLinkIds shouldBe emptyList()
                    }
                }
            }
            listOf(null, "cascade", "move").forEach { mode ->
                When("빈 source의 $mode 옵션을 계획하면") {
                    val plan =
                        FolderDeletionDomainService.plan(
                            source = private,
                            links = emptyList(),
                            callerId = callerId,
                            options =
                                FolderDeletionOptions.from(
                                    onDelete = mode,
                                    targetFolderId = if (mode == "move") RandomFixture.randomId() else null,
                                ),
                        )
                    Then("세 집합이 모두 비어 있다") {
                        plan.deleteLinkIds shouldBe emptyList()
                        plan.moveLinkIds shouldBe emptyList()
                        plan.detachLinkIds shouldBe emptyList()
                    }
                }
            }
            When("타인 링크만 있는 개인 source의 cascade를 계획하면") {
                val plan =
                    FolderDeletionDomainService.plan(
                        source = private,
                        links = others,
                        callerId = callerId,
                        options = FolderDeletionOptions.from(onDelete = "cascade", targetFolderId = null),
                    )
                Then("모든 타인 링크를 detach한다") {
                    plan.deleteLinkIds shouldBe emptyList()
                    plan.moveLinkIds shouldBe emptyList()
                    plan.detachLinkIds shouldBe others.map { it.id }
                }
            }
        }
    })
