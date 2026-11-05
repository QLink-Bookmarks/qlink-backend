@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.bookmarkimport.domain.ImportSourceLink
import com.qlink.bookmarkimport.domain.service.ImportPlanDomainService
import com.qlink.bookmarkimport.dto.CreateImportRequest
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportSummary
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.bookmarkimport.worker.ImportThumbnailSignal
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.worker.LinkContentSignal
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CreateImportService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val bookmarkImportRepository: BookmarkImportRepository,
    private val importThumbnailSignal: ImportThumbnailSignal,
    private val linkContentRepository: LinkContentRepository,
    private val linkContentSignal: LinkContentSignal,
) {
    suspend fun createImport(
        loginId: Long,
        request: CreateImportRequest,
    ): ImportSummary =
        tx
            .required { saveImport(loginId, request) }
            .also {
                importThumbnailSignal.wake()
                linkContentSignal.wake()
            }

    private suspend fun saveImport(
        loginId: Long,
        request: CreateImportRequest,
    ): ImportSummary {
        bookmarkImportRepository.lockOwner(loginId)
        userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_OWNER_NOT_FOUND)

        return findSummary(loginId, request.clientRequestId) ?: insertImport(loginId, request)
    }

    private suspend fun findSummary(
        loginId: Long,
        clientRequestId: Uuid,
    ): ImportSummary? =
        bookmarkImportRepository
            .findByOwnerIdAndClientRequestId(ownerId = loginId, clientRequestId = clientRequestId)
            ?.let { summaryOf(it) }

    private suspend fun insertImport(
        loginId: Long,
        request: CreateImportRequest,
    ): ImportSummary {
        val now = Clock.System.now()
        val plan =
            ImportPlanDomainService.plan(
                source = request.toSource(),
                ownerId = loginId,
                myFolders = folderRepository.findAllByOwnerId(loginId),
                myUrls = linkRepository.findAllUrlsByOwnerId(loginId),
                now = now,
            )
        val bookmarkImport =
            bookmarkImportRepository.insert(
                BookmarkImport.create(
                    ownerId = loginId,
                    clientRequestId = request.clientRequestId,
                    fileName = request.fileName,
                    linkCount = plan.linkCount,
                    folderCount = plan.folderCount,
                    skippedCount = plan.skippedCount,
                    failedCount = plan.failedCount,
                ),
            )
        val importId = bookmarkImport.id!!
        val savedFolders = folderRepository.insertAll(ImportPlanDomainService.newFolders(plan))
        val savedLinks = linkRepository.insertAll(ImportPlanDomainService.links(plan = plan, savedNewFolders = savedFolders))
        linkContentRepository.insertAll(
            savedLinks.map {
                LinkContent.create(
                    linkId = it.id!!,
                    sourceUrl = it.url,
                    priority = ContentPriority.BULK,
                    now = now,
                )
            },
        )
        val savedImportFolders =
            bookmarkImportRepository.insertFolders(
                ImportPlanDomainService.importFolders(plan = plan, importId = importId, savedNewFolders = savedFolders),
            )
        bookmarkImportRepository.insertLinks(
            ImportPlanDomainService.importLinks(importId = importId, savedLinks = savedLinks, savedImportFolders = savedImportFolders),
        )
        bookmarkImportRepository.insertFailures(plan.failures.map { ImportFailure.create(importId = importId, failure = it) })

        return summaryOf(bookmarkImport)
    }

    private suspend fun summaryOf(bookmarkImport: BookmarkImport): ImportSummary =
        bookmarkImport.toSummary(bookmarkImportRepository.countItems(listOf(bookmarkImport.id!!)).single())

    private fun CreateImportRequest.toSource(): ImportSource =
        ImportSource(
            folders =
                folders.map {
                    ImportSourceFolder(key = it.key, folderId = it.folderId, name = it.name, createdAt = it.createdAt)
                },
            links =
                links.map {
                    ImportSourceLink(key = it.key, url = it.url, title = it.title, folderKey = it.folderKey, createdAt = it.createdAt)
                },
        )

    private fun BookmarkImport.toSummary(counts: ImportItemCountsQuery): ImportSummary =
        ImportSummary.of(
            bookmarkImport = this,
            state =
                summaryState(
                    finishedThumbnailCount = counts.finishedThumbnailCount,
                    removableLinkCount = counts.removableLinkCount,
                    removableNewFolderCount = counts.removableNewFolderCount,
                ),
        )
}
