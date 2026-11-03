package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import com.qlink.folder.domain.Folder
import com.qlink.link.domain.Link
import java.net.URI
import java.text.Normalizer
import kotlin.time.Instant

private val TRACKING_QUERY_KEYS = setOf("fbclid", "gclid", "igshid", "mc_cid", "mc_eid")
private const val TRACKING_QUERY_PREFIX = "utm_"
private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)

class ImportSourceFolder(
    val key: String,
    val folderId: Long? = null,
    val name: String,
    val createdAt: Instant? = null,
)

class ImportSourceLink(
    val key: String,
    val url: String,
    val title: String? = null,
    val folderKey: String? = null,
    val createdAt: Instant? = null,
)

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
        folders.forEach { validateFolderName(it.name.comparableName()) }
        folders
            .mapTo(HashSet()) { it.key }
            .let { keys -> links.all { link -> link.folderKey == null || link.folderKey in keys } }
            .requireTrue(ErrorCode.IMPORT_FOLDER_KEY_NOT_FOUND)
    }

    fun preview(
        myFolders: List<Folder>,
        myUrls: List<String>,
    ): ImportPreview {
        val owned = OwnedFolders(myFolders)
        val numberedNames = NumberedNames(myFolders.map { it.name } + folders.map { it.name })
        val savedUrls = myUrls.mapNotNullTo(HashSet(), ::normalizeUrl)
        val requestUrls = HashSet<String>()

        return ImportPreview(
            folders = folders.map { previewFolder(it, owned, numberedNames) },
            links =
                links.map { link ->
                    ImportPreviewLink(
                        key = link.key,
                        status =
                            when (val url = normalizeUrl(link.url)) {
                                null -> ImportPreviewLinkStatus.NEW
                                in savedUrls -> ImportPreviewLinkStatus.SAVED
                                else -> if (requestUrls.add(url)) ImportPreviewLinkStatus.NEW else ImportPreviewLinkStatus.IN_FILE_DUPLICATE
                            },
                    )
                },
        )
    }

    fun plan(
        ownerId: Long,
        myFolders: List<Folder>,
        myUrls: List<String>,
        now: Instant,
    ): ImportPlan {
        val checkedLinks = links.map { it to it.failureReason(ownerId, now) }
        val validLinks = checkedLinks.filter { (_, reason) -> reason == null }.map { (link, _) -> link }
        val knownUrls = myUrls.mapNotNullTo(HashSet(), ::normalizeUrl)
        val createdLinks = validLinks.filter { link -> normalizeUrl(link.url)?.let(knownUrls::add) ?: true }
        val folderByKey = planFolders(ownerId, myFolders, createdLinks.mapNotNullTo(HashSet()) { it.folderKey }, now)

        return ImportPlan(
            ownerId = ownerId,
            folders = folderByKey.values.distinct(),
            links =
                createdLinks.map { link ->
                    ImportPlanLink(
                        url = link.url,
                        title = link.title.orEmpty(),
                        createdAt = link.createdAt.orNow(now),
                        folder = link.folderKey?.let(folderByKey::getValue),
                    )
                },
            failures =
                checkedLinks.mapNotNull { (link, reason) ->
                    reason?.let { ImportPlanFailure(title = link.title.orEmpty(), url = link.url, reason = it) }
                },
            skippedCount = validLinks.size - createdLinks.size,
        )
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
        ownerId: Long,
        myFolders: List<Folder>,
        usedKeys: Set<String>,
        now: Instant,
    ): Map<String, ImportPlanFolder> {
        val owned = OwnedFolders(myFolders)
        val numberedNames = NumberedNames(myFolders.map { it.name } + folders.map { it.name })
        val usedFolders = folders.filter { it.key in usedKeys }
        val targetByKey = usedFolders.associate { it.key to owned.targetOf(it, numberedNames) }
        val planFolderByTarget =
            usedFolders
                .groupBy { targetByKey.getValue(it.key) }
                .mapValues { (target, entries) -> target.toPlanFolder(ownerId, entries.minOf { it.createdAt.orNow(now) }) }

        return targetByKey.mapValues { (_, target) -> planFolderByTarget.getValue(target) }
    }

    private fun ImportSourceLink.failureReason(
        ownerId: Long,
        now: Instant,
    ): ImportFailureReason? =
        when {
            url.length > LINK_URL_MAX_LENGTH -> {
                ImportFailureReason.URL_INVALID
            }

            title.isNullOrBlank() || title.length > LINK_TITLE_MAX_LENGTH -> {
                ImportFailureReason.TITLE_INVALID
            }

            else -> {
                try {
                    Link.createImported(ownerId = ownerId, folderId = null, url = url, title = title, createdAt = now)
                    null
                } catch (e: BusinessException) {
                    ImportFailureReason.from(e.errorCode)
                }
            }
        }

    private fun validateFolderName(name: String) {
        name.requireNotBlank(ErrorCode.IMPORT_FOLDER_NAME_BLANK)
        name.requireNotOver(FOLDER_NAME_MAX_LENGTH, ErrorCode.IMPORT_FOLDER_NAME_OVER_MAX)
    }

    companion object {
        const val LINKS_MAX_COUNT = 3000
        const val FOLDERS_MAX_COUNT = 3000
        const val FOLDER_NAME_MAX_LENGTH = 100
        const val LINK_URL_MAX_LENGTH = 2048
        const val LINK_TITLE_MAX_LENGTH = 300
    }
}

private sealed interface FolderTarget {
    fun toPlanFolder(
        ownerId: Long,
        createdAt: Instant,
    ): ImportPlanFolder
}

private data class ExistingFolderTarget(
    val folder: Folder,
) : FolderTarget {
    override fun toPlanFolder(
        ownerId: Long,
        createdAt: Instant,
    ): ImportPlanFolder = ImportPlanFolder(kind = ImportFolderKind.EXISTING, folder = folder)
}

private data class NewFolderTarget(
    val name: String,
) : FolderTarget {
    override fun toPlanFolder(
        ownerId: Long,
        createdAt: Instant,
    ): ImportPlanFolder =
        ImportPlanFolder(
            kind = ImportFolderKind.NEW,
            folder = Folder.createImported(ownerId = ownerId, name = name, createdAt = createdAt),
        )
}

private class OwnedFolders(
    folders: List<Folder>,
) {
    private val unsharedById = folders.filterNot { it.isShared }.associateBy { it.id }
    private val unsharedByName = folders.filterNot { it.isShared }.firstByComparableName()
    private val sharedNames = folders.filter { it.isShared }.mapTo(HashSet()) { it.name.comparableName() }

    fun unsharedNamed(name: String): Folder? = unsharedByName[name.comparableName()]

    fun hasSharedNamed(name: String): Boolean = name.comparableName() in sharedNames

    fun targetOf(
        source: ImportSourceFolder,
        numberedNames: NumberedNames,
    ): FolderTarget {
        val name = source.name.comparableName()

        return (source.folderId?.let(unsharedById::get) ?: unsharedNamed(name))
            ?.let(::ExistingFolderTarget)
            ?: NewFolderTarget(name = if (hasSharedNamed(name)) numberedNames.of(name) else name)
    }

    private fun List<Folder>.firstByComparableName(): Map<String, Folder> =
        groupBy { it.name.comparableName() }.mapValues { (_, folders) -> folders.first() }
}

private class NumberedNames(
    takenNames: List<String>,
) {
    private val taken = takenNames.mapTo(HashSet()) { it.comparableName() }
    private val assigned = HashMap<String, String>()

    fun of(name: String): String = assigned.getOrPut(name.comparableName()) { next(name) }

    fun next(name: String): String =
        generateSequence(2) { it + 1 }
            .map { numbered(name.trim(), it) }
            .first { taken.add(it.comparableName()) }

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

private fun String.comparableName(): String = Normalizer.normalize(trim(), Normalizer.Form.NFC)

private fun Instant?.orNow(now: Instant): Instant = this?.takeIf { it <= now } ?: now

private fun List<String>.isDistinct(): Boolean = size == toSet().size

private fun normalizeUrl(url: String): String? =
    runCatching { URI(url.trim()) }
        .getOrNull()
        ?.takeIf { it.scheme != null && it.host != null }
        ?.let { uri ->
            val scheme = uri.scheme.lowercase()
            val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
            val port =
                uri.port
                    .takeUnless { it == -1 || it == DEFAULT_PORTS[scheme] }
                    ?.let { ":$it" }
                    .orEmpty()
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val query =
                uri.rawQuery
                    ?.split('&')
                    ?.filter { it.isNotEmpty() && !it.isTrackingParameter() }
                    ?.sortedBy { it.substringBefore('=') }
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "&", prefix = "?")
                    .orEmpty()

            "$scheme://$userInfo${uri.host.lowercase()}$port$path$query"
        }

private fun String.isTrackingParameter(): Boolean =
    substringBefore('=')
        .lowercase()
        .let { it.startsWith(TRACKING_QUERY_PREFIX) || it in TRACKING_QUERY_KEYS }
