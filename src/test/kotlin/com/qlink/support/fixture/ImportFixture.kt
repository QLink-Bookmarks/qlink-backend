@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.support.fixture

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportFolderState
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ImportLinkState
import com.qlink.bookmarkimport.domain.ImportPlan
import com.qlink.bookmarkimport.domain.ImportPlanFailure
import com.qlink.bookmarkimport.domain.ImportPlanFolder
import com.qlink.bookmarkimport.domain.ImportPlanLink
import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.bookmarkimport.domain.ImportSourceLink
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ImportFixture {
    fun randomKey(): String = "key-${RandomFixture.randomId()}"

    fun randomFileName(): String = "${RandomFixture.randomSentenceWithMax(200)}.html"

    fun createSourceFolder(
        key: String = randomKey(),
        folderId: Long? = null,
        name: String = "${RandomFixture.randomSentenceWithMax(80)} ${RandomFixture.randomId()}",
        createdAt: Instant? = null,
    ): ImportSourceFolder =
        ImportSourceFolder(
            key = key,
            folderId = folderId,
            name = name,
            createdAt = createdAt,
        )

    fun createSourceLink(
        key: String = randomKey(),
        url: String = randomUniqueUrl(),
        title: String? = RandomFixture.randomSentenceWithMax(300),
        folderKey: String? = null,
        createdAt: Instant? = null,
    ): ImportSourceLink =
        ImportSourceLink(
            key = key,
            url = url,
            title = title,
            folderKey = folderKey,
            createdAt = createdAt,
        )

    fun createSource(
        folders: List<ImportSourceFolder> = emptyList(),
        links: List<ImportSourceLink> = listOf(createSourceLink()),
    ): ImportSource =
        ImportSource(
            folders = folders,
            links = links,
        )

    fun createSourceLinks(count: Int): List<ImportSourceLink> = List(count) { createSourceLink() }

    fun createSourceFolders(count: Int): List<ImportSourceFolder> = List(count) { createSourceFolder() }

    fun randomUniqueUrl(): String = "https://example-${RandomFixture.randomId()}.com/${RandomFixture.randomId()}"

    fun createBookmarkImportOf(
        ownerId: Long,
        clientRequestId: Uuid = Uuid.random(),
        fileName: String = randomFileName(),
        status: ImportStatus = ImportStatus.COMPLETED,
        linkCount: Int = RandomFixture.randomInt(1, 100),
        folderCount: Int = RandomFixture.randomInt(0, 10),
        skippedCount: Int = RandomFixture.randomInt(0, 10),
        failedCount: Int = 0,
        reversedAt: Instant? = null,
    ): BookmarkImport =
        BookmarkImport(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            clientRequestId = clientRequestId,
            fileName = fileName,
            status = status,
            linkCount = linkCount,
            folderCount = folderCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            reversedAt = reversedAt,
        )

    fun createImportFolderOf(
        importId: Long,
        folderId: Long,
        kind: ImportFolderKind = ImportFolderKind.NEW,
        name: String = RandomFixture.randomSentenceWithMax(100),
        reversedAt: Instant? = null,
    ): ImportFolder =
        ImportFolder(
            id = RandomFixture.randomId(),
            importId = importId,
            folderId = folderId,
            kind = kind,
            name = name,
            reversedAt = reversedAt,
        )

    fun createImportLinkOf(
        importId: Long,
        linkId: Long,
        importFolderId: Long? = null,
        url: String = RandomFixture.randomUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        thumbnailStatus: ThumbnailStatus = ThumbnailStatus.PENDING,
        startedAt: Instant? = null,
        reversedAt: Instant? = null,
    ): ImportLink =
        ImportLink(
            id = RandomFixture.randomId(),
            importId = importId,
            linkId = linkId,
            importFolderId = importFolderId,
            url = url,
            title = title,
            thumbnailStatus = thumbnailStatus,
            startedAt = startedAt,
            reversedAt = reversedAt,
        )

    fun createFolderStateOf(
        folder: ImportFolder,
        isDeleted: Boolean = false,
        isShared: Boolean = false,
    ): ImportFolderState = folder.withState(isDeleted = isDeleted, isShared = isShared)

    fun createLinkStateOf(
        link: ImportLink,
        isDeleted: Boolean = false,
        isEdited: Boolean = false,
    ): ImportLinkState = link.withState(isDeleted = isDeleted, isEdited = isEdited)

    fun createExistingPlanFolder(
        folderId: Long = RandomFixture.randomId(),
        name: String = "기존 ${RandomFixture.randomId()}",
    ): ImportPlanFolder = ImportPlanFolder.existing(folderId = folderId, name = name)

    fun createNewPlanFolder(
        name: String = "새 ${RandomFixture.randomId()}",
        createdAt: Instant = RandomFixture.randomPastInstant(),
    ): ImportPlanFolder = ImportPlanFolder.new(name = name, createdAt = createdAt)

    fun createPlanLinkOf(
        folder: ImportPlanFolder?,
        url: String = randomUniqueUrl(),
        title: String = RandomFixture.randomSentenceWithMax(300),
        createdAt: Instant = RandomFixture.randomPastInstant(),
    ): ImportPlanLink =
        ImportPlanLink(
            url = url,
            title = title,
            createdAt = createdAt,
            folder = folder,
        )

    fun createPlanFailureOf(reason: ImportFailureReason = ImportFailureReason.entries.random()): ImportPlanFailure =
        ImportPlanFailure(
            title = RandomFixture.randomSentenceWithMax(300),
            url = RandomFixture.randomUrl(),
            reason = reason,
        )

    fun createPlanOf(
        folders: List<ImportPlanFolder>,
        links: List<ImportPlanLink>,
        failures: List<ImportPlanFailure> = emptyList(),
        ownerId: Long = RandomFixture.randomId(),
        skippedCount: Int = RandomFixture.randomInt(0, 10),
    ): ImportPlan =
        ImportPlan(
            ownerId = ownerId,
            folders = folders,
            links = links,
            failures = failures,
            skippedCount = skippedCount,
        )

    fun createImportFailureOf(
        importId: Long,
        reason: ImportFailureReason = ImportFailureReason.entries.random(),
    ): ImportFailure =
        ImportFailure(
            importId = importId,
            title = RandomFixture.randomSentenceWithMax(300),
            url = RandomFixture.randomUrl(),
            reason = reason,
        )
}
