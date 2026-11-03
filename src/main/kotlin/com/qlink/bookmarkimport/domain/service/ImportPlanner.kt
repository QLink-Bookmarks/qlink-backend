package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderMatchType
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ImportPlan
import com.qlink.bookmarkimport.domain.ImportPlanFailure
import com.qlink.bookmarkimport.domain.ImportPlanFolder
import com.qlink.bookmarkimport.domain.ImportPlanLink
import com.qlink.bookmarkimport.domain.ImportPreview
import com.qlink.bookmarkimport.domain.ImportPreviewFolder
import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.bookmarkimport.domain.ImportSourceLink
import com.qlink.common.error.BusinessException
import com.qlink.folder.domain.Folder
import com.qlink.link.domain.Link
import kotlin.time.Instant

object ImportPlanner {
    fun preview(
        source: ImportSource,
        myFolders: List<Folder>,
        myUrls: List<String>,
    ): ImportPreview {
        val owned = OwnedFolders(myFolders)
        val numberedNames = NumberedNames(myFolders.map { it.name } + source.folders.map { it.name })

        return ImportPreview(
            folders = source.folders.map { previewFolder(it, owned, numberedNames) },
            links = source.previewLinks(myUrls),
        )
    }

    fun plan(
        source: ImportSource,
        ownerId: Long,
        myFolders: List<Folder>,
        myUrls: List<String>,
        now: Instant,
    ): ImportPlan {
        val failures = source.links.mapNotNull { link -> link.planFailureReason(ownerId = ownerId, now = now)?.let { link to it } }
        val createdLinks = source.newLinks(failedKeys = failures.mapTo(HashSet()) { (link, _) -> link.key }, myUrls = myUrls)
        val folderByKey =
            planFolders(usedFolders = source.foldersOf(createdLinks), myFolders = myFolders, sourceFolders = source.folders, now = now)

        return ImportPlan(
            ownerId = ownerId,
            folders = folderByKey.values.distinct(),
            links =
                createdLinks.map { link ->
                    ImportPlanLink(
                        url = link.url,
                        title = link.title.orEmpty(),
                        createdAt = link.createdAtOr(now),
                        folder = link.folderKey?.let(folderByKey::getValue),
                    )
                },
            failures =
                failures.map { (link, reason) ->
                    ImportPlanFailure(title = link.title.orEmpty(), url = link.url, reason = reason)
                },
            skippedCount = source.links.size - failures.size - createdLinks.size,
        )
    }

    fun newFolders(plan: ImportPlan): List<Folder> =
        plan.newFolders.map {
            Folder.createImported(ownerId = plan.ownerId, name = it.name, createdAt = it.createdAt!!)
        }

    fun links(
        plan: ImportPlan,
        savedNewFolders: List<Folder>,
    ): List<Link> {
        val newFolderIdByName = savedNewFolders.idByName()

        return plan.links.map { link ->
            Link.createImported(
                ownerId = plan.ownerId,
                folderId = plan.folderIdOf(link = link, newFolderIdByName = newFolderIdByName),
                url = link.url,
                title = link.title,
                createdAt = link.createdAt,
            )
        }
    }

    fun importFolders(
        plan: ImportPlan,
        importId: Long,
        savedNewFolders: List<Folder>,
    ): List<ImportFolder> = plan.toImportFolders(importId = importId, newFolderIdByName = savedNewFolders.idByName())

    fun importLinks(
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

    private fun previewFolder(
        folder: ImportSourceFolder,
        owned: OwnedFolders,
        numberedNames: NumberedNames,
    ): ImportPreviewFolder {
        val name = folder.name.trim()

        return owned.unsharedNamed(name)?.let {
            ImportPreviewFolder(key = folder.key, matchType = ImportFolderMatchType.EXISTING, folderId = it.id, suggestedName = it.name)
        } ?: when {
            owned.hasSharedNamed(name) -> {
                ImportPreviewFolder(
                    key = folder.key,
                    matchType = ImportFolderMatchType.SHARED_CONFLICT,
                    folderId = null,
                    suggestedName = numberedNames.next(name),
                )
            }

            else -> {
                ImportPreviewFolder(key = folder.key, matchType = ImportFolderMatchType.NEW, folderId = null, suggestedName = name)
            }
        }
    }

    private fun planFolders(
        usedFolders: List<ImportSourceFolder>,
        myFolders: List<Folder>,
        sourceFolders: List<ImportSourceFolder>,
        now: Instant,
    ): Map<String, ImportPlanFolder> {
        val owned = OwnedFolders(myFolders)
        val numberedNames = NumberedNames(myFolders.map { it.name } + sourceFolders.map { it.name })
        val targetByKey = usedFolders.associate { it.key to owned.targetOf(it, numberedNames) }
        val planFolderByTarget =
            usedFolders
                .groupBy { targetByKey.getValue(it.key) }
                .mapValues { (target, entries) -> target.toPlanFolder(entries.minOf { it.createdAtOr(now) }) }

        return targetByKey.mapValues { (_, target) -> planFolderByTarget.getValue(target) }
    }

    private fun ImportSourceLink.planFailureReason(
        ownerId: Long,
        now: Instant,
    ): ImportFailureReason? =
        failureReason() ?: try {
            Link.createImported(ownerId = ownerId, folderId = null, url = url, title = title.orEmpty(), createdAt = now)
            null
        } catch (e: BusinessException) {
            ImportFailureReason.from(e.errorCode)
        }

    private fun List<Folder>.idByName(): Map<String, Long> = associate { it.name to it.id!! }
}

private sealed interface FolderTarget {
    fun toPlanFolder(createdAt: Instant): ImportPlanFolder
}

private data class ExistingFolderTarget(
    val folderId: Long,
    val name: String,
) : FolderTarget {
    override fun toPlanFolder(createdAt: Instant): ImportPlanFolder = ImportPlanFolder.existing(folderId = folderId, name = name)
}

private data class NewFolderTarget(
    val name: String,
) : FolderTarget {
    override fun toPlanFolder(createdAt: Instant): ImportPlanFolder = ImportPlanFolder.new(name = name, createdAt = createdAt)
}

private class OwnedFolders(
    folders: List<Folder>,
) {
    private val unsharedById = folders.filterNot { it.isShared }.associateBy { it.id }
    private val unsharedByName = folders.filterNot { it.isShared }.firstByComparableName()
    private val sharedNames = folders.filter { it.isShared }.mapTo(HashSet()) { ImportSourceFolder.comparableNameOf(it.name) }

    fun unsharedNamed(name: String): Folder? = unsharedByName[ImportSourceFolder.comparableNameOf(name)]

    fun hasSharedNamed(name: String): Boolean = ImportSourceFolder.comparableNameOf(name) in sharedNames

    fun targetOf(
        source: ImportSourceFolder,
        numberedNames: NumberedNames,
    ): FolderTarget {
        val name = source.comparableName

        return (source.folderId?.let(unsharedById::get) ?: unsharedNamed(name))
            ?.let { ExistingFolderTarget(folderId = it.id!!, name = it.name) }
            ?: NewFolderTarget(name = if (hasSharedNamed(name)) numberedNames.of(name) else name)
    }

    private fun List<Folder>.firstByComparableName(): Map<String, Folder> =
        groupBy { ImportSourceFolder.comparableNameOf(it.name) }.mapValues { (_, folders) -> folders.first() }
}

private class NumberedNames(
    takenNames: List<String>,
) {
    private val taken = takenNames.mapTo(HashSet()) { ImportSourceFolder.comparableNameOf(it) }
    private val assigned = HashMap<String, String>()

    fun of(name: String): String = assigned.getOrPut(ImportSourceFolder.comparableNameOf(name)) { next(name) }

    fun next(name: String): String =
        generateSequence(2) { it + 1 }
            .map { numbered(name.trim(), it) }
            .first { taken.add(ImportSourceFolder.comparableNameOf(it)) }

    private fun numbered(
        name: String,
        number: Int,
    ): String {
        val suffix = " $number"

        return name
            .take(ImportSource.FOLDER_NAME_MAX_LENGTH - suffix.length)
            .let { head -> if (head.lastOrNull()?.isHighSurrogate() == true) head.dropLast(1) else head }
            .plus(suffix)
    }
}
