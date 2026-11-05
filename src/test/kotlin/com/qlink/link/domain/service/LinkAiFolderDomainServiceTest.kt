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
            val currentId = RandomFixture.randomLong(1, Long.MAX_VALUE - 1)
            val link = LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = currentId)
            val candidate = FolderFixture.createValidUnsharedFolder(ownerId = callerId)
            When("시작 folderId가 같고 candidate 소유자이면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderId = currentId,
                        candidate = candidate,
                        member = null,
                    )
                Then("candidate를 선택한다") { folderId shouldBe candidate.id }
            }
            When("시작 folderId가 같고 공유 candidate의 쓰기 멤버이면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = RandomFixture.randomId())
                val member = FolderMemberFixture.createMember(folderId = shared.id!!, userId = callerId)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderId = currentId,
                        candidate = shared,
                        member = member,
                    )
                Then("candidate를 선택한다") { folderId shouldBe shared.id }
            }
            When("시작 이후 다른 폴더로 옮겨졌으면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderId = currentId + 1,
                        candidate = candidate,
                        member = null,
                    )
                Then("현재 배정을 보존한다") { folderId shouldBe currentId }
            }
            When("시작 이후 폴더에서 빠졌으면") {
                val detached = LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = null)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = detached,
                        startedFolderId = currentId,
                        candidate = candidate,
                        member = null,
                    )
                Then("null 배정을 보존한다") { folderId shouldBe null }
            }
            When("시작 시 폴더가 없었고 지금도 없으면") {
                val detached = LinkFixture.createRandomLinkOf(ownerId = callerId, folderId = null)
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = detached,
                        startedFolderId = null,
                        candidate = candidate,
                        member = null,
                    )
                Then("candidate를 선택한다") { folderId shouldBe candidate.id }
            }
            When("시작 이후 다른 폴더로 옮겼다가 시작 폴더로 되돌아왔으면") {
                // 알려진 한계: 시작 snapshot 비교라 A→B→A 이동은 감지하지 않고 AI candidate를 배정한다.
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderId = currentId,
                        candidate = candidate,
                        member = null,
                    )
                Then("candidate를 선택한다") { folderId shouldBe candidate.id }
            }
            When("candidate가 삭제됐으면") {
                val folderId =
                    LinkAiFolderDomainService.resolve(
                        link = link,
                        startedFolderId = currentId,
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
                        startedFolderId = currentId,
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
                            startedFolderId = currentId,
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
                        startedFolderId = currentId,
                        candidate = private,
                        member = member,
                    )
                Then("현재 유효 배정을 보존한다") { folderId shouldBe currentId }
            }
        }
    })
