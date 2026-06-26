package com.qlink.foldermember.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class FolderMemberTest :
    BehaviorSpec({
        Given("폴더 멤버 도메인 모델 테스트") {
            When("생성자로 폴더 멤버를 만들면") {
                val now = Clock.System.now()
                val folderMember =
                    FolderMember(
                        folderId = 1,
                        userId = 2,
                        userName = "tester",
                        role = MemberRole.OWNER,
                        joinedAt = now,
                        createdAt = now,
                        updatedAt = now,
                    )

                Then("값과 data class 동작이 유지된다") {
                    folderMember.folderId shouldBe 1
                    folderMember.userName shouldBe "tester"
                    folderMember.role shouldBe MemberRole.OWNER
                    folderMember.copy() shouldBe folderMember
                    folderMember.copy(folderId = 2) shouldNotBe folderMember
                    folderMember.copy(userId = 3) shouldNotBe folderMember
                    folderMember.copy(userName = "other") shouldNotBe folderMember
                    folderMember.copy(role = MemberRole.MEMBER) shouldNotBe folderMember
                    folderMember.copy(joinedAt = now + 1.seconds) shouldNotBe folderMember
                    folderMember.copy(createdAt = now + 2.seconds) shouldNotBe folderMember
                    folderMember.copy(updatedAt = now + 3.seconds) shouldNotBe folderMember
                    folderMember.equals("member") shouldBe false
                    folderMember.hashCode() shouldBe folderMember.copy().hashCode()
                }
            }

            When("소유자 멤버를 만들면") {
                val now = Clock.System.now()
                val folderMember =
                    FolderMember.owner(
                        folderId = 1,
                        userId = 2,
                        userName = "owner",
                        joinedAt = now,
                    )

                Then("OWNER 역할과 생성 시간이 채워진다") {
                    folderMember.folderId shouldBe 1
                    folderMember.userId shouldBe 2
                    folderMember.userName shouldBe "owner"
                    folderMember.role shouldBe MemberRole.OWNER
                    folderMember.joinedAt shouldBe now
                    folderMember.createdAt shouldBe now
                    folderMember.updatedAt shouldBe now
                }
            }

            When("일반 멤버를 만들면") {
                val now = Clock.System.now()
                val folderMember =
                    FolderMember.member(
                        folderId = 1,
                        userId = 2,
                        userName = "member",
                        joinedAt = now,
                    )

                Then("MEMBER 역할과 생성 시간이 채워진다") {
                    folderMember.folderId shouldBe 1
                    folderMember.userId shouldBe 2
                    folderMember.userName shouldBe "member"
                    folderMember.role shouldBe MemberRole.MEMBER
                    folderMember.joinedAt shouldBe now
                    folderMember.createdAt shouldBe now
                    folderMember.updatedAt shouldBe now
                }
            }

            When("링크 쓰기 권한을 확인하면") {
                val now = Clock.System.now()

                Then("OWNER와 MEMBER 모두 링크를 쓸 수 있다") {
                    MemberRole.OWNER.canWriteLink() shouldBe true
                    MemberRole.MEMBER.canWriteLink() shouldBe true
                    FolderMember.owner(folderId = 1, userId = 2, userName = "owner", joinedAt = now).canWriteLink() shouldBe true
                    FolderMember.member(folderId = 1, userId = 3, userName = "member", joinedAt = now).canWriteLink() shouldBe true
                }
            }
        }
    })
