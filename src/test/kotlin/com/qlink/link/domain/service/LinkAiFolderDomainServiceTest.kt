package com.qlink.link.domain.service

import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LinkAiFolderDomainServiceTest :
    BehaviorSpec({
        Given("최신 링크와 AI 시작 snapshot") {
            val callerId = RandomFixture.randomId()
            val revision = RandomFixture.randomLong(1, Long.MAX_VALUE - 1)
            val currentId = RandomFixture.randomId()
            val link = LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = currentId, folderAssignmentRevision = revision)
            val candidate = FolderFixture.createValidUnsharedFolder(ownerId = callerId)
            When("revision이 같고 candidate 소유자이면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = candidate.id,
                        candidate = candidate,
                        member = null,
                    )
                Then("candidate를 선택한다") { folderId shouldBe candidate.id }
            }
            When("revision이 같고 공유 candidate의 쓰기 멤버이면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = RandomFixture.randomId())
                val member = FolderMemberFixture.createMember(folderId = shared.id!!, userId = callerId)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = shared.id,
                        candidate = shared,
                        member = member,
                    )
                Then("candidate를 선택한다") { folderId shouldBe shared.id }
            }
            When("revision이 같고 AI가 미분류를 선택하면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = null,
                        candidate = null,
                        member = null,
                    )
                Then("기존 폴더에서 미분류로 변경한다") { folderId shouldBe null }
            }
            When("revision이 바뀌고 AI가 미분류를 선택하면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision - 1,
                        candidateFolderId = null,
                        candidate = null,
                        member = null,
                    )
                Then("최신 폴더 배정을 보존한다") { folderId shouldBe currentId }
            }
            When("revision이 바뀌었으면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision - 1,
                        candidateFolderId = candidate.id,
                        candidate = candidate,
                        member = null,
                    )
                Then("현재 배정을 보존한다") { folderId shouldBe currentId }
            }
            When("detach 이후 revision이 바뀌었으면") {
                val detached = LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = null, folderAssignmentRevision = revision)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = detached,
                        startedFolderAssignmentRevision = revision - 1,
                        candidateFolderId = candidate.id,
                        candidate = candidate,
                        member = null,
                    )
                Then("null 배정을 보존한다") { folderId shouldBe null }
            }
            When("candidate가 삭제됐으면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = RandomFixture.randomId(),
                        candidate = null,
                        member = null,
                    )
                Then("현재 유효 배정을 보존한다") { folderId shouldBe currentId }
            }
            When("공유 candidate의 쓰기 권한이 사라졌으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = RandomFixture.randomId())
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = shared.id,
                        candidate = shared,
                        member = null,
                    )
                Then("현재 유효 배정을 보존한다") { folderId shouldBe currentId }
            }
            listOf("다른 폴더", "다른 사용자").forEach { relationship ->
                When("공유 candidate에 $relationship 의 member를 전달하면") {
                    val shared = FolderFixture.createSharedFolderOf(ownerId = RandomFixture.randomId())
                    val member =
                        FolderMemberFixture.createMember(
                            folderId = if (relationship == "다른 폴더") RandomFixture.randomId() else shared.id!!,
                            userId = if (relationship == "다른 사용자") RandomFixture.randomId() else callerId,
                        )
                    val folderId =
                        LinkAiFolderDomainService.resolve(
                            link = link,
                            startedFolderAssignmentRevision = revision,
                            candidateFolderId = shared.id,
                            candidate = shared,
                            member = member,
                        )
                    Then("현재 유효 배정을 보존한다") { folderId shouldBe currentId }
                }
            }
            When("candidate가 개인 폴더로 바뀌고 stale member만 있으면") {
                val private = FolderFixture.createValidUnsharedFolder(ownerId = RandomFixture.randomId())
                val member = FolderMemberFixture.createMember(folderId = private.id!!, userId = callerId)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderAssignmentRevision = revision,
                        candidateFolderId = private.id,
                        candidate = private,
                        member = member,
                    )
                Then("현재 유효 배정을 보존한다") { folderId shouldBe currentId }
            }
        }
    })
