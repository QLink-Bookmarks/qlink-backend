package com.qlink.bookmarkimport.repository.table

import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object BookmarkImportFolders : Table("bookmark_import_folders") {
    val id = long("id").autoIncrement()
    val importId = reference("import_id", BookmarkImports.id, onDelete = ReferenceOption.CASCADE)
    val folderId = long("folder_id")
    val kind = enumerationByName<ImportFolderKind>("kind", 10)
    val name = varchar("name", 100)
    val reversedAt = timestamp("reversed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("bookmark_import_folders_import_id_idx", false, importId)
    }
}

fun ResultRow.toImportFolderDomain(): ImportFolder =
    ImportFolder(
        id = this[BookmarkImportFolders.id],
        importId = this[BookmarkImportFolders.importId],
        folderId = this[BookmarkImportFolders.folderId],
        kind = this[BookmarkImportFolders.kind],
        name = this[BookmarkImportFolders.name],
        reversedAt = this[BookmarkImportFolders.reversedAt]?.toKotlinInstant(),
        createdAt = this[BookmarkImportFolders.createdAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(folder: ImportFolder) {
    this[BookmarkImportFolders.importId] = folder.importId
    this[BookmarkImportFolders.folderId] = folder.folderId
    this[BookmarkImportFolders.kind] = folder.kind
    this[BookmarkImportFolders.name] = folder.name
    this[BookmarkImportFolders.reversedAt] = folder.reversedAt?.toJavaInstant()
}
