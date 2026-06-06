package com.qlink.folder.service

import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.link.service.GetLinkDetailService
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

class SharedFolderReadServiceTest :
    BaseServiceTest({
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val getFoldersService = koinGet<GetFoldersService>()
        val getLinkDetailService = koinGet<GetLinkDetailService>()

        Given("공유 폴더 조회 권한 테스트") {
            lateinit var owner: User
            lateinit var member: User

            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                member = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("공유 폴더 멤버가 폴더 목록을 조회하면") {
                lateinit var folder: Folder

                beforeTest {
                    folder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(
                                ownerId = owner.id!!,
                                sharedAt = Clock.System.now(),
                            ),
                        )
                    folderMemberRepository.insertIfAbsent(
                        FolderMemberFixture.createMember(
                            folderId = folder.id!!,
                            userId = member.id!!,
                            userName = member.nickname,
                        ),
                    )
                }

                val get = suspend {
                    getFoldersService.getFolders(
                        loginId = member.id!!,
                        query = null,
                        order = "latest",
                        scrollRequest = ScrollRequest(size = 10),
                    )
                }

                Then("본인이 소유하지 않은 공유 폴더가 소유자 정보와 함께 반환된다") {
                    val response = get()
                    val actual = response.contents.single { it.id == folder.id }

                    actual.ownerId shouldBe owner.id
                    actual.ownerNickname shouldBe owner.nickname
                    actual.isShared shouldBe true
                }
            }

            When("공유 폴더 멤버가 링크 상세를 조회하면") {
                lateinit var folder: Folder
                lateinit var link: Link

                beforeTest {
                    folder =
                        folderRepository.insert(
                            FolderFixture.createFolderWith(
                                ownerId = owner.id!!,
                                sharedAt = Clock.System.now(),
                            ),
                        )
                    folderMemberRepository.insertIfAbsent(
                        FolderMemberFixture.createMember(
                            folderId = folder.id!!,
                            userId = member.id!!,
                            userName = member.nickname,
                        ),
                    )
                    link =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(
                                ownerId = owner.id!!,
                                folderId = folder.id,
                            ),
                        )
                }

                val get = suspend { getLinkDetailService.getLinkDetail(member.id!!, link.id!!) }

                Then("링크 상세 조회에 성공한다") {
                    val response = get()

                    response.id shouldBe link.id
                    response.folderId shouldBe folder.id
                }
            }
        }
    })
