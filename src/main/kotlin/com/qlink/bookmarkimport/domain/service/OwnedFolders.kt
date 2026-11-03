package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.folder.domain.Folder

internal class OwnedFolders(
    folders: List<Folder>,
) {
    private val unsharedById = folders.filterNot { it.isShared }.associateBy { it.id }
    private val unsharedByName = folders.filterNot { it.isShared }.firstByComparableName()
    private val sharedNames = folders.filter { it.isShared }.mapTo(HashSet()) { ImportSourceFolder.comparableNameOf(it.name) }

    fun unsharedNamed(name: String): Folder? = unsharedByName[ImportSourceFolder.comparableNameOf(name)]

    fun hasSharedNamed(name: String): Boolean = ImportSourceFolder.comparableNameOf(name) in sharedNames

    fun targetOf(
        source: ImportSourceFolder,
        numberedNames: NumberedFolderNames,
    ): FolderTarget {
        val name = source.comparableName

        return (source.folderId?.let(unsharedById::get) ?: unsharedNamed(name))
            ?.let { FolderTarget(folderId = it.id!!, name = it.name) }
            ?: FolderTarget(folderId = null, name = if (hasSharedNamed(name)) numberedNames.of(name) else name)
    }

    private fun List<Folder>.firstByComparableName(): Map<String, Folder> =
        groupBy { ImportSourceFolder.comparableNameOf(it.name) }.mapValues { (_, folders) -> folders.first() }
}
