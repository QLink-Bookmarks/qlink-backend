@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.support.fixture

import com.qlink.bookmarkimport.dto.CreateImportFolderRequest
import com.qlink.bookmarkimport.dto.CreateImportLinkRequest
import com.qlink.bookmarkimport.dto.CreateImportPreviewFolderRequest
import com.qlink.bookmarkimport.dto.CreateImportPreviewLinkRequest
import com.qlink.bookmarkimport.dto.CreateImportPreviewRequest
import com.qlink.bookmarkimport.dto.CreateImportRequest
import com.qlink.bookmarkimport.dto.CreateImportReversalRequest
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ImportRequestFixture {
    fun randomFolderName(): String = "${RandomFixture.randomSentenceWithMax(80)} ${RandomFixture.randomId()}"

    fun createPreviewFolderRequest(
        key: String = ImportFixture.randomKey(),
        name: String = randomFolderName(),
    ): CreateImportPreviewFolderRequest = CreateImportPreviewFolderRequest(key = key, name = name)

    fun createPreviewLinkRequest(
        key: String = ImportFixture.randomKey(),
        url: String = ImportFixture.randomUniqueUrl(),
    ): CreateImportPreviewLinkRequest = CreateImportPreviewLinkRequest(key = key, url = url)

    fun createPreviewRequest(
        folders: List<CreateImportPreviewFolderRequest> = emptyList(),
        links: List<CreateImportPreviewLinkRequest> = listOf(createPreviewLinkRequest()),
    ): CreateImportPreviewRequest = CreateImportPreviewRequest(folders = folders, links = links)

    fun createFolderRequest(
        key: String = ImportFixture.randomKey(),
        folderId: Long? = null,
        name: String = randomFolderName(),
        createdAt: Instant? = null,
    ): CreateImportFolderRequest =
        CreateImportFolderRequest(
            key = key,
            folderId = folderId,
            name = name,
            createdAt = createdAt,
        )

    fun createLinkRequest(
        key: String = ImportFixture.randomKey(),
        url: String = ImportFixture.randomUniqueUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        folderKey: String? = null,
        createdAt: Instant? = null,
    ): CreateImportLinkRequest =
        CreateImportLinkRequest(
            key = key,
            url = url,
            title = title,
            folderKey = folderKey,
            createdAt = createdAt,
        )

    fun createLinkRequestsIn(
        folder: CreateImportFolderRequest?,
        count: Int,
    ): List<CreateImportLinkRequest> = List(count) { createLinkRequest(folderKey = folder?.key) }

    fun createImportRequest(
        clientRequestId: Uuid = Uuid.random(),
        fileName: String = ImportFixture.randomFileName(),
        folders: List<CreateImportFolderRequest> = emptyList(),
        links: List<CreateImportLinkRequest> = listOf(createLinkRequest()),
    ): CreateImportRequest =
        CreateImportRequest(
            clientRequestId = clientRequestId,
            fileName = fileName,
            folders = folders,
            links = links,
        )

    fun createReversalRequest(
        linkIds: List<Long> = emptyList(),
        folderIds: List<Long> = emptyList(),
    ): CreateImportReversalRequest = CreateImportReversalRequest(linkIds = linkIds, folderIds = folderIds)
}
