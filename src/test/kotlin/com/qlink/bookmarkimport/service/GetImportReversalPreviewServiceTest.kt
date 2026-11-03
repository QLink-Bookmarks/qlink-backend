@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

class GetImportReversalPreviewServiceTest :
    BaseServiceTest({
        val getImportReversalPreviewService = koinGet<GetImportReversalPreviewService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()

        Given("가져온 뒤 일부를 수정·삭제·추가·공유한 내 가져오기") {
            lateinit var user: User
            lateinit var activeFolder: Folder
            lateinit var sharedFolder: Folder
            lateinit var deletedLink: Link
            lateinit var editedLink: Link
            lateinit var importedLink: Link
            lateinit var addedLaterLink: Link
            lateinit var bookmarkImport: BookmarkImport
            lateinit var importLinkByLinkId: Map<Long, ImportLink>

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                activeFolder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = user.id!!))
                sharedFolder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = user.id!!))
                deletedLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = activeFolder.id))
                editedLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = activeFolder.id))
                importedLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = sharedFolder.id))
                bookmarkImport =
                    bookmarkImportRepository.insert(
                        ImportFixture.createBookmarkImportOf(ownerId = user.id!!, linkCount = 3, folderCount = 2),
                    )

                val importFolderIdByFolderId =
                    bookmarkImportRepository
                        .insertFolders(
                            listOf(activeFolder, sharedFolder).map {
                                ImportFixture.createImportFolderOf(
                                    importId = bookmarkImport.id!!,
                                    folderId = it.id!!,
                                    kind = ImportFolderKind.NEW,
                                    name = it.name,
                                )
                            },
                        ).associate { it.folderId to it.id }
                importLinkByLinkId =
                    bookmarkImportRepository
                        .insertLinks(
                            listOf(deletedLink, editedLink, importedLink).map { link ->
                                ImportFixture.createImportLinkOf(
                                    importId = bookmarkImport.id!!,
                                    linkId = link.id!!,
                                    importFolderId = importFolderIdByFolderId.getValue(link.folderId!!),
                                    url = link.url,
                                    title = if (link == editedLink) "${link.title.take(200)} 가져올 때 제목" else link.title,
                                )
                            },
                        ).associateBy { it.linkId }

                linkRepository.deleteById(deletedLink.id!!)
                addedLaterLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = activeFolder.id))
                folderRepository.update(
                    sharedFolder.update(name = sharedFolder.name, emoji = sharedFolder.emoji, sharedAt = Clock.System.now()),
                )
            }

            When("미리보기를 조회하면") {
                val get =
                    suspend {
                        getImportReversalPreviewService.getReversalPreview(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("지운 링크는 DELETED로 가져올 때 폴더 아래, 수정한 링크는 EDITED, NEW 폴더에 넣은 링크는 ADDED_LATER, 공유한 폴더는 SHARED다") {
                    val folderById = get().folders.associateBy { it.folderId }

                    folderById.getValue(activeFolder.id).state shouldBe ReversalFolderState.ACTIVE
                    folderById.getValue(activeFolder.id).links.map { it.linkId to it.state } shouldContainExactlyInAnyOrder
                        listOf(
                            deletedLink.id!! to ReversalLinkState.DELETED,
                            editedLink.id!! to ReversalLinkState.EDITED,
                            addedLaterLink.id!! to ReversalLinkState.ADDED_LATER,
                        )
                    folderById.getValue(sharedFolder.id).state shouldBe ReversalFolderState.SHARED
                    folderById.getValue(sharedFolder.id).links.map { it.linkId to it.state } shouldContainExactlyInAnyOrder
                        listOf(importedLink.id!! to ReversalLinkState.IMPORTED)
                }
            }

            When("일부를 이미 되돌렸으면") {
                lateinit var reversedFolder: Folder

                beforeTest {
                    val now = Clock.System.now()
                    reversedFolder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = user.id!!))
                    val reversedImportFolder =
                        bookmarkImportRepository
                            .insertFolders(
                                listOf(
                                    ImportFixture.createImportFolderOf(
                                        importId = bookmarkImport.id!!,
                                        folderId = reversedFolder.id!!,
                                        kind = ImportFolderKind.NEW,
                                        name = reversedFolder.name,
                                    ),
                                ),
                            ).single()
                    bookmarkImportRepository.reverseFolders(importFolderIds = listOf(reversedImportFolder.id!!), reversedAt = now)
                    folderRepository.deleteById(reversedFolder.id!!)
                    bookmarkImportRepository.reverseLinks(
                        importLinkIds = listOf(importLinkByLinkId.getValue(editedLink.id!!).id!!),
                        reversedAt = now,
                    )
                    linkRepository.deleteById(editedLink.id!!)
                }

                val get =
                    suspend {
                        getImportReversalPreviewService.getReversalPreview(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("되돌린 링크·폴더는 REVERSED다") {
                    val folders = get().folders

                    folders.single { it.folderId == reversedFolder.id }.state shouldBe ReversalFolderState.REVERSED
                    folders.flatMap { it.links }.single { it.linkId == editedLink.id }.state shouldBe ReversalLinkState.REVERSED
                }
            }

            When("없는 기록 id면") {
                val get =
                    suspend {
                        getImportReversalPreviewService.getReversalPreview(loginId = user.id!!, importId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("미리보기를 조회하면") {
                val get =
                    suspend {
                        getImportReversalPreviewService.getReversalPreview(
                            loginId = RandomFixture.randomId(),
                            importId = RandomFixture.randomId(),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
