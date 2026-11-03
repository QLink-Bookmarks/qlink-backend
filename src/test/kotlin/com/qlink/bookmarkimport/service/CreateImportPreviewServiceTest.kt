package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportFolderMatchType
import com.qlink.bookmarkimport.domain.ImportPreviewLinkStatus
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.ImportRequestFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class CreateImportPreviewServiceTest :
    BaseServiceTest({
        val createImportPreviewService = koinGet<CreateImportPreviewService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()

        Given("폴더·링크가 있는 로그인 사용자") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var unsharedFolder: Folder
            lateinit var sharedFolder: Folder
            lateinit var savedLink: Link
            var savedLinkSeed = 0L

            beforeTest {
                savedLinkSeed = RandomFixture.randomId()
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                unsharedFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(ownerId = user.id!!, name = ImportRequestFixture.randomFolderName()),
                    )
                sharedFolder =
                    folderRepository.insert(
                        FolderFixture.createSharedFolderOf(ownerId = user.id!!, name = ImportRequestFixture.randomFolderName()),
                    )
                savedLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            url = "https://example-$savedLinkSeed.com/path-$savedLinkSeed?b=1&utm_source=mail",
                        ),
                    )
            }

            When("미리보기를 요청하면") {
                lateinit var existingKey: String
                lateinit var sharedKey: String
                lateinit var newKey: String
                lateinit var linkKey: String

                beforeTest {
                    existingKey = ImportFixture.randomKey()
                    sharedKey = ImportFixture.randomKey()
                    newKey = ImportFixture.randomKey()
                    linkKey = ImportFixture.randomKey()
                }

                val preview =
                    suspend {
                        createImportPreviewService.createPreview(
                            loginId = user.id!!,
                            request =
                                ImportRequestFixture.createPreviewRequest(
                                    folders =
                                        listOf(
                                            ImportRequestFixture.createPreviewFolderRequest(key = existingKey, name = unsharedFolder.name),
                                            ImportRequestFixture.createPreviewFolderRequest(key = sharedKey, name = sharedFolder.name),
                                            ImportRequestFixture.createPreviewFolderRequest(key = newKey),
                                        ),
                                    links = listOf(ImportRequestFixture.createPreviewLinkRequest(key = linkKey)),
                                ),
                        )
                    }

                Then("응답에 폴더 matchType과 링크 status가 담기고, 가져오기 기록·폴더·링크 수는 그대로다") {
                    val response = preview()

                    response.folders.map { it.key to it.matchType } shouldContainExactly
                        listOf(
                            existingKey to ImportFolderMatchType.EXISTING,
                            sharedKey to ImportFolderMatchType.SHARED_CONFLICT,
                            newKey to ImportFolderMatchType.NEW,
                        )
                    response.folders.first().folderId shouldBe unsharedFolder.id
                    response.links.map { it.key to it.status } shouldContainExactly listOf(linkKey to ImportPreviewLinkStatus.NEW)
                    bookmarkImportRepository.search(ownerId = user.id!!, cursor = null, size = 10).shouldBeEmpty()
                    folderRepository.findAllByOwnerId(user.id!!).map { it.id } shouldContainExactlyInAnyOrder
                        listOf(unsharedFolder.id, sharedFolder.id)
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldContainExactly listOf(savedLink.url)
                }
            }

            When("다른 사용자가 공유해 준(내가 멤버인) 폴더와 이름이 같으면") {
                lateinit var memberFolder: Folder

                beforeTest {
                    memberFolder =
                        folderRepository.insert(
                            FolderFixture.createSharedFolderOf(ownerId = otherUser.id!!, name = ImportRequestFixture.randomFolderName()),
                        )
                    folderMemberRepository.insertIfAbsent(
                        FolderMemberFixture.createMember(folderId = memberFolder.id!!, userId = user.id!!),
                    )
                }

                val preview =
                    suspend {
                        createImportPreviewService.createPreview(
                            loginId = user.id!!,
                            request =
                                ImportRequestFixture.createPreviewRequest(
                                    folders = listOf(ImportRequestFixture.createPreviewFolderRequest(name = memberFolder.name)),
                                ),
                        )
                    }

                Then("그 폴더는 비교 대상이 아니어서 NEW") {
                    val response = preview()

                    response.folders.single().matchType shouldBe ImportFolderMatchType.NEW
                    response.folders.single().folderId shouldBe null
                }
            }

            When("다른 사용자의 링크와 URL이 같으면") {
                lateinit var otherLink: Link

                beforeTest {
                    otherLink =
                        linkRepository.insert(
                            LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!, url = ImportFixture.randomUniqueUrl()),
                        )
                }

                val preview =
                    suspend {
                        createImportPreviewService.createPreview(
                            loginId = user.id!!,
                            request =
                                ImportRequestFixture.createPreviewRequest(
                                    links = listOf(ImportRequestFixture.createPreviewLinkRequest(url = otherLink.url)),
                                ),
                        )
                    }

                Then("NEW") {
                    preview().links.single().status shouldBe ImportPreviewLinkStatus.NEW
                }
            }

            When("내 링크와 정규화 URL이 같으면") {
                val preview =
                    suspend {
                        createImportPreviewService.createPreview(
                            loginId = user.id!!,
                            request =
                                ImportRequestFixture.createPreviewRequest(
                                    links =
                                        listOf(
                                            ImportRequestFixture.createPreviewLinkRequest(
                                                url = "HTTPS://EXAMPLE-$savedLinkSeed.COM/path-$savedLinkSeed/?b=1#section",
                                            ),
                                        ),
                                ),
                        )
                    }

                Then("SAVED") {
                    preview().links.single().status shouldBe ImportPreviewLinkStatus.SAVED
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("미리보기를 요청하면") {
                val preview =
                    suspend {
                        createImportPreviewService.createPreview(
                            loginId = RandomFixture.randomId(),
                            request = ImportRequestFixture.createPreviewRequest(),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_PREVIEW_OWNER_NOT_FOUND.message) {
                        preview()
                    }
                }
            }
        }
    })
