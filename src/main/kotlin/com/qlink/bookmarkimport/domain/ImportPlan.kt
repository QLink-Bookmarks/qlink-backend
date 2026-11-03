package com.qlink.bookmarkimport.domain

import com.qlink.folder.domain.Folder
import com.qlink.link.domain.Link
import kotlin.time.Instant

class ImportPlan(
    val ownerId: Long,
    val folders: List<ImportPlanFolder>,
    val links: List<ImportPlanLink>,
    val failures: List<ImportPlanFailure>,
    val skippedCount: Int,
) {
    val newFolders: List<Folder>
        get() = folders.filter { it.kind == ImportFolderKind.NEW }.map { it.folder }

    val linkCount: Int
        get() = links.size

    val folderCount: Int
        get() = newFolders.size

    val failedCount: Int
        get() = failures.size

    fun toLinks(savedNewFolders: List<Folder>): List<Link> {
        val savedByName = savedNewFolders.associateBy { it.name }

        return links.map { link ->
            Link.createImported(
                ownerId = ownerId,
                folderId = link.folder?.savedFolderIdIn(savedByName),
                url = link.url,
                title = link.title,
                createdAt = link.createdAt,
            )
        }
    }

    fun toImportFolders(
        importId: Long,
        savedNewFolders: List<Folder>,
    ): List<ImportFolder> {
        val savedByName = savedNewFolders.associateBy { it.name }

        return folders.map { folder ->
            ImportFolder.create(
                importId = importId,
                folderId = folder.savedFolderIdIn(savedByName),
                kind = folder.kind,
                name = folder.folder.name,
            )
        }
    }

    fun toImportLinks(
        importId: Long,
        savedLinks: List<Link>,
        savedImportFolders: List<ImportFolder>,
    ): List<ImportLink> {
        val importFolderIdByFolderId = savedImportFolders.associate { it.folderId to it.id }

        return savedLinks.map { link ->
            ImportLink.create(
                importId = importId,
                linkId = link.id!!,
                importFolderId = link.folderId?.let(importFolderIdByFolderId::get),
                url = link.url,
                title = link.title,
            )
        }
    }

    fun toImportFailures(importId: Long): List<ImportFailure> =
        failures.map {
            ImportFailure(
                importId = importId,
                title = it.title,
                url = it.url,
                reason = it.reason,
            )
        }

    private fun ImportPlanFolder.savedFolderIdIn(savedByName: Map<String, Folder>): Long =
        folder.id ?: savedByName.getValue(folder.name).id!!
}

class ImportPlanFolder(
    val kind: ImportFolderKind,
    val folder: Folder,
)

class ImportPlanLink(
    val url: String,
    val title: String,
    val createdAt: Instant,
    val folder: ImportPlanFolder?,
)

class ImportPlanFailure(
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
)
