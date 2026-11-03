package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ImportReversalFacts
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState
import com.qlink.bookmarkimport.dto.ImportReversal
import com.qlink.bookmarkimport.dto.ImportReversalPreview
import com.qlink.bookmarkimport.dto.ReversalPreviewFolder
import com.qlink.bookmarkimport.dto.ReversalPreviewLink
import com.qlink.folder.domain.Folder
import com.qlink.link.domain.Link
import kotlin.time.Instant

object ImportTrackDomainService {
    fun folderName(
        folder: ImportFolder,
        current: Folder?,
    ): String = folder.displayName(currentName = current?.name)

    fun reversalPreview(
        folders: List<ImportFolder>,
        links: List<ImportLink>,
        currentLinks: List<Link>,
        currentFolders: List<Folder>,
    ): ImportReversalPreview {
        val currentLinkById = currentLinks.associateBy { it.id }
        val currentFolderById = currentFolders.associateBy { it.id }
        val importFolderById = folders.associateBy { it.id }
        val importFolderByFolderId = folders.associateBy { it.folderId }
        val importedLinkIds = links.mapTo(HashSet()) { it.linkId }
        val facts = reversalFacts(folders = folders, links = links, currentLinks = currentLinks, currentFolders = currentFolders)
        val importedLinks =
            links.map { row ->
                val current = currentLinkById[row.linkId]
                val folderId = if (current != null) current.folderId else row.importFolderId?.let { importFolderById[it]?.folderId }

                folderId to
                    ReversalPreviewLink(
                        linkId = row.linkId,
                        title = current?.title ?: row.title,
                        url = current?.url ?: row.url,
                        state = row.reversalState(facts),
                    )
            }
        val addedLaterLinks =
            currentLinks
                .filter { it.id !in importedLinkIds && importFolderByFolderId[it.folderId]?.kind == ImportFolderKind.NEW }
                .map {
                    it.folderId to
                        ReversalPreviewLink(linkId = it.id!!, title = it.title, url = it.url, state = ReversalLinkState.ADDED_LATER)
                }
        val linksByFolderId = (importedLinks + addedLaterLinks).groupBy({ it.first }, { it.second })
        val importGroups =
            folders.map { row ->
                val current = currentFolderById[row.folderId]

                ReversalPreviewFolder(
                    kind = row.kind,
                    state = row.reversalState(facts),
                    folderId = row.folderId,
                    name = folderName(row, current),
                    emoji = current?.emoji,
                    links = linksByFolderId[row.folderId].orEmpty(),
                )
            }
        val otherGroups =
            linksByFolderId
                .filterKeys { it != null && it !in importFolderByFolderId }
                .map { (folderId, groupLinks) ->
                    val current = currentFolderById[folderId]

                    ReversalPreviewFolder(
                        kind = ImportFolderKind.EXISTING,
                        state = ReversalFolderState.ACTIVE,
                        folderId = folderId,
                        name = current?.name,
                        emoji = current?.emoji,
                        links = groupLinks,
                    )
                }
        val unfiledGroup =
            linksByFolderId[null]?.let { groupLinks ->
                ReversalPreviewFolder(
                    kind = ImportFolderKind.UNFILED,
                    state = ReversalFolderState.ACTIVE,
                    folderId = null,
                    name = null,
                    emoji = null,
                    links = groupLinks,
                )
            }

        return ImportReversalPreview(folders = importGroups + otherGroups + listOfNotNull(unfiledGroup))
    }

    fun reverse(
        bookmarkImport: BookmarkImport,
        linkIds: List<Long>,
        folderIds: List<Long>,
        folders: List<ImportFolder>,
        links: List<ImportLink>,
        currentLinks: List<Link>,
        currentFolders: List<Folder>,
        now: Instant,
    ): ImportReversal {
        val targets =
            bookmarkImport.reversalTargets(
                linkIds = linkIds,
                folderIds = folderIds,
                folders = folders,
                links = links,
                facts = reversalFacts(folders = folders, links = links, currentLinks = currentLinks, currentFolders = currentFolders),
            )
        val linkByLinkId = links.associateBy { it.linkId }
        val newFolderByFolderId = folders.filter { it.kind == ImportFolderKind.NEW }.associateBy { it.folderId }

        return ImportReversal(
            bookmarkImport = bookmarkImport.reverse(targets = targets, now = now),
            linkIds = targets.linkIds,
            folderIds = targets.folderIds,
            importLinks = targets.linkIds.map { linkByLinkId.getValue(it).reverse(now) },
            importFolders = targets.folderIds.map { newFolderByFolderId.getValue(it).reverse(now) },
        )
    }

    fun prepareThumbnail(
        link: ImportLink,
        current: Link?,
    ): ImportLink = link.prepareThumbnail(isDeleted = current == null, hasPreview = current?.hasPreview == true)

    private fun reversalFacts(
        folders: List<ImportFolder>,
        links: List<ImportLink>,
        currentLinks: List<Link>,
        currentFolders: List<Folder>,
    ): ImportReversalFacts {
        val currentFolderIds = currentFolders.mapNotNullTo(HashSet()) { it.id }
        val currentLinkById = currentLinks.associateBy { it.id }

        return ImportReversalFacts(
            deletedFolderIds = folders.map { it.folderId }.filterNotTo(HashSet()) { it in currentFolderIds },
            sharedFolderIds = currentFolders.filter { it.isShared }.mapNotNullTo(HashSet()) { it.id },
            deletedLinkIds = links.map { it.linkId }.filterNotTo(HashSet()) { it in currentLinkById },
            editedLinkIds =
                links
                    .filter { row -> currentLinkById[row.linkId]?.isEditedOrFilledFrom(row) == true }
                    .mapTo(HashSet()) { it.linkId },
        )
    }

    private fun Link.isEditedOrFilledFrom(row: ImportLink): Boolean = isEditedFrom(url = row.url, title = row.title) || hasUserContent()
}
