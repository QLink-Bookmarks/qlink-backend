package com.qlink.bookmarkimport.repository.table

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object BookmarkImportLinks : Table("bookmark_import_links") {
    val id = long("id").autoIncrement()
    val importId = reference("import_id", BookmarkImports.id, onDelete = ReferenceOption.CASCADE)
    val linkId = long("link_id")
    val importFolderId =
        reference("import_folder_id", BookmarkImportFolders.id, onDelete = ReferenceOption.CASCADE).nullable()
    val url = text("url")
    val title = varchar("title", 300)
    val thumbnailStatus = enumerationByName<ThumbnailStatus>("thumbnail_status", 10).default(ThumbnailStatus.PENDING)
    val startedAt = timestamp("started_at").nullable()
    val reversedAt = timestamp("reversed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("bookmark_import_links_import_id_idx", false, importId)
    }
}

fun ResultRow.toImportLinkDomain(): ImportLink =
    ImportLink(
        id = this[BookmarkImportLinks.id],
        importId = this[BookmarkImportLinks.importId],
        linkId = this[BookmarkImportLinks.linkId],
        importFolderId = this[BookmarkImportLinks.importFolderId],
        url = this[BookmarkImportLinks.url],
        title = this[BookmarkImportLinks.title],
        thumbnailStatus = this[BookmarkImportLinks.thumbnailStatus],
        startedAt = this[BookmarkImportLinks.startedAt]?.toKotlinInstant(),
        reversedAt = this[BookmarkImportLinks.reversedAt]?.toKotlinInstant(),
        createdAt = this[BookmarkImportLinks.createdAt].toKotlinInstant(),
        updatedAt = this[BookmarkImportLinks.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(link: ImportLink) {
    this[BookmarkImportLinks.importId] = link.importId
    this[BookmarkImportLinks.linkId] = link.linkId
    this[BookmarkImportLinks.importFolderId] = link.importFolderId
    this[BookmarkImportLinks.url] = link.url
    this[BookmarkImportLinks.title] = link.title
    this[BookmarkImportLinks.thumbnailStatus] = link.thumbnailStatus
    this[BookmarkImportLinks.startedAt] = link.startedAt?.toJavaInstant()
    this[BookmarkImportLinks.reversedAt] = link.reversedAt?.toJavaInstant()
}

fun UpdateBuilder<*>.refreshImportLinkUpdatedAt() {
    this[BookmarkImportLinks.updatedAt] = Clock.System.now().toJavaInstant()
}
