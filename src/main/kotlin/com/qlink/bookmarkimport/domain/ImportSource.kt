package com.qlink.bookmarkimport.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
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
) {
    val comparableName: String
        get() = comparableNameOf(name)

    fun createdAtOr(now: Instant): Instant = createdAt.orNow(now)

    companion object {
        fun comparableNameOf(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFC)
    }
}

class ImportSourceLink(
    val key: String,
    val url: String,
    val title: String? = null,
    val folderKey: String? = null,
    val createdAt: Instant? = null,
) {
    fun failureReason(): ImportFailureReason? =
        when {
            url.length > ImportSource.LINK_URL_MAX_LENGTH -> ImportFailureReason.URL_INVALID
            title.isNullOrBlank() || title.length > ImportSource.LINK_TITLE_MAX_LENGTH -> ImportFailureReason.TITLE_INVALID
            else -> null
        }

    fun createdAtOr(now: Instant): Instant = createdAt.orNow(now)
}

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

    fun previewLinks(myUrls: List<String>): List<ImportPreviewLink> {
        val savedUrls = myUrls.mapNotNullTo(HashSet(), ::normalizeUrl)
        val requestUrls = HashSet<String>()

        return links.map { link ->
            ImportPreviewLink(
                key = link.key,
                status =
                    when (val url = normalizeUrl(link.url)) {
                        null -> ImportPreviewLinkStatus.NEW
                        in savedUrls -> ImportPreviewLinkStatus.SAVED
                        else -> if (requestUrls.add(url)) ImportPreviewLinkStatus.NEW else ImportPreviewLinkStatus.IN_FILE_DUPLICATE
                    },
            )
        }
    }

    fun newLinks(
        failedKeys: Set<String>,
        myUrls: List<String>,
    ): List<ImportSourceLink> {
        val knownUrls = myUrls.mapNotNullTo(HashSet(), ::normalizeUrl)

        return links
            .filterNot { it.key in failedKeys }
            .filter { link -> normalizeUrl(link.url)?.let(knownUrls::add) ?: true }
    }

    fun foldersOf(links: List<ImportSourceLink>): List<ImportSourceFolder> =
        links
            .mapNotNullTo(HashSet()) { it.folderKey }
            .let { keys -> folders.filter { it.key in keys } }

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
