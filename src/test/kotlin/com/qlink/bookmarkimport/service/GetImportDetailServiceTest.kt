@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.link.repository.table.Links
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.ImportRequestFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi

class GetImportDetailServiceTest :
    BaseServiceTest({
        val getImportDetailService = koinGet<GetImportDetailService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()
        val tx = koinGet<TransactionRunner>()

        Given("새 폴더·기존 폴더·미분류 링크와 실패가 있는 내 가져오기") {
            lateinit var user: User
            lateinit var existingFolder: Folder
            lateinit var newFolder: Folder
            lateinit var newFolderLink: Link
            lateinit var existingFolderLink: Link
            lateinit var unfiledLink: Link
            lateinit var bookmarkImport: BookmarkImport
            lateinit var newImportFolder: ImportFolder
            lateinit var failure: ImportFailure

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                existingFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                newFolder = folderRepository.insert(FolderFixture.createImportedFolderOf(ownerId = user.id!!))
                newFolderLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = newFolder.id))
                existingFolderLink =
                    linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!, folderId = existingFolder.id))
                unfiledLink = linkRepository.insert(LinkFixture.createImportedLinkOf(ownerId = user.id!!))
                bookmarkImport =
                    bookmarkImportRepository.insert(
                        ImportFixture.createBookmarkImportOf(ownerId = user.id!!, linkCount = 3, folderCount = 1),
                    )

                val importFolders =
                    bookmarkImportRepository.insertFolders(
                        listOf(
                            ImportFixture.createImportFolderOf(
                                importId = bookmarkImport.id!!,
                                folderId = newFolder.id!!,
                                kind = ImportFolderKind.NEW,
                                name = newFolder.name,
                            ),
                            ImportFixture.createImportFolderOf(
                                importId = bookmarkImport.id!!,
                                folderId = existingFolder.id!!,
                                kind = ImportFolderKind.EXISTING,
                                name = existingFolder.name,
                            ),
                        ),
                    )
                newImportFolder = importFolders.first()
                val importFolderIdByFolderId = importFolders.associate { it.folderId to it.id }
                bookmarkImportRepository.insertLinks(
                    listOf(newFolderLink, existingFolderLink, unfiledLink).map { link ->
                        ImportFixture.createImportLinkOf(
                            importId = bookmarkImport.id!!,
                            linkId = link.id!!,
                            importFolderId = link.folderId?.let(importFolderIdByFolderId::getValue),
                            url = link.url,
                            title = link.title,
                        )
                    },
                )
                failure =
                    bookmarkImportRepository
                        .insertFailures(
                            listOf(
                                ImportFixture.createImportFailureOf(
                                    importId = bookmarkImport.id!!,
                                    reason = ImportFailureReason.URL_INVALID,
                                ),
                            ),
                        ).single()
            }

            When("상세를 조회하면") {
                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("링크가 가져올 때 폴더별로 EXISTING·NEW·UNFILED 묶음에 담기고 실패 항목이 사유와 함께 담긴다") {
                    val response = get()

                    response.id shouldBe bookmarkImport.id
                    response.folders.map { folder ->
                        Triple(folder.kind, folder.folderId, folder.links.map { it.linkId })
                    } shouldContainExactly
                        listOf(
                            Triple(ImportFolderKind.NEW, newFolder.id, listOf(newFolderLink.id)),
                            Triple(ImportFolderKind.EXISTING, existingFolder.id, listOf(existingFolderLink.id)),
                            Triple(ImportFolderKind.UNFILED, null, listOf(unfiledLink.id)),
                        )
                    response.failures.map { Triple(it.title, it.url, it.reason) } shouldContainExactly
                        listOf(Triple(failure.title, failure.url, ImportFailureReason.URL_INVALID))
                }
            }

            When("가져온 링크를 다른 폴더로 옮겼으면") {
                beforeTest {
                    tx.required {
                        Links.update(where = { Links.id eq newFolderLink.id!! }) { it[Links.folderId] = existingFolder.id }
                    }
                }

                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("여전히 가져올 때 폴더 묶음에 있다") {
                    get()
                        .folders
                        .single { it.kind == ImportFolderKind.NEW }
                        .links
                        .map { it.linkId } shouldContainExactly
                        listOf(newFolderLink.id)
                }
            }

            When("새 폴더 이름을 바꿨으면") {
                val renamed = ImportRequestFixture.randomFolderName()

                beforeTest {
                    folderRepository.update(newFolder.update(name = renamed, emoji = newFolder.emoji))
                }

                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("지금 이름이다") {
                    get().folders.single { it.kind == ImportFolderKind.NEW }.name shouldBe renamed
                }
            }

            When("새 폴더를 지웠으면") {
                beforeTest {
                    linkRepository.detachFolder(newFolder.id!!)
                    folderRepository.deleteById(newFolder.id!!)
                }

                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = user.id!!, importId = bookmarkImport.id!!)
                    }

                Then("가져올 때 이름이다") {
                    get().folders.single { it.kind == ImportFolderKind.NEW }.name shouldBe newImportFolder.name
                }
            }

            When("없는 기록 id면") {
                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = user.id!!, importId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("상세를 조회하면") {
                val get =
                    suspend {
                        getImportDetailService.getImportDetail(loginId = RandomFixture.randomId(), importId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_OWNER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
