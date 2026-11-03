package com.qlink.bookmarkimport.domain

import com.qlink.common.collection.isDistinct
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import com.qlink.common.text.toNormalizedUrlOrNull

class ImportSource(
    val folders: List<ImportSourceFolder>,
    val links: List<ImportSourceLink>,
) {
    init {
        links.isNotEmpty().requireTrue(ErrorCode.IMPORT_LINKS_EMPTY)
        (links.size <= LINKS_MAX_COUNT).requireTrue(ErrorCode.IMPORT_LINKS_OVER_MAX)
        (folders.size <= FOLDERS_MAX_COUNT).requireTrue(ErrorCode.IMPORT_FOLDERS_OVER_MAX)
        links.map { it.key }.isDistinct().requireTrue(ErrorCode.IMPORT_KEY_DUPLICATED)
        folders.map { it.key }.isDistinct().requireTrue(ErrorCode.IMPORT_KEY_DUPLICATED)
        folders.forEach { validateFolderName(it.comparableName) }
        folders
            .mapTo(HashSet()) { it.key }
            .let { keys -> links.all { link -> link.folderKey == null || link.folderKey in keys } }
            .requireTrue(ErrorCode.IMPORT_FOLDER_KEY_NOT_FOUND)
    }

    fun previewLinkStatuses(myUrls: List<String>): Map<String, ImportPreviewLinkStatus> {
        val savedUrls = myUrls.mapNotNullTo(HashSet(), ::comparableUrlOf)
        val requestUrls = HashSet<String>()

        return links.associate { link ->
            link.key to
                when (val url = comparableUrlOf(link.url)) {
                    null -> ImportPreviewLinkStatus.NEW
                    in savedUrls -> ImportPreviewLinkStatus.SAVED
                    else -> if (requestUrls.add(url)) ImportPreviewLinkStatus.NEW else ImportPreviewLinkStatus.IN_FILE_DUPLICATE
                }
        }
    }

    fun newLinks(
        failedKeys: Set<String>,
        myUrls: List<String>,
    ): List<ImportSourceLink> {
        val knownUrls = myUrls.mapNotNullTo(HashSet(), ::comparableUrlOf)

        return links
            .filterNot { it.key in failedKeys }
            .filter { link -> comparableUrlOf(link.url)?.let(knownUrls::add) ?: true }
    }

    fun foldersOf(links: List<ImportSourceLink>): List<ImportSourceFolder> =
        links
            .mapNotNullTo(HashSet()) { it.folderKey }
            .let { keys -> folders.filter { it.key in keys } }

    private fun validateFolderName(name: String) {
        name.requireNotBlank(ErrorCode.IMPORT_FOLDER_NAME_BLANK)
        name.requireNotOver(ImportSourceFolder.NAME_MAX_LENGTH, ErrorCode.IMPORT_FOLDER_NAME_OVER_MAX)
    }

    private fun comparableUrlOf(url: String): String? =
        url.toNormalizedUrlOrNull(ignoredQueryKeys = TRACKING_QUERY_KEYS, ignoredQueryKeyPrefixes = TRACKING_QUERY_KEY_PREFIXES)

    companion object {
        const val LINKS_MAX_COUNT = 3000
        const val FOLDERS_MAX_COUNT = 3000

        private val TRACKING_QUERY_KEYS = setOf("fbclid", "gclid", "igshid", "mc_cid", "mc_eid")
        private val TRACKING_QUERY_KEY_PREFIXES = setOf("utm_")
    }
}
