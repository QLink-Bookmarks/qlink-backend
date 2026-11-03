@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.repository.table

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi

object BookmarkImports : Table("bookmark_imports") {
    val id = long("id").autoIncrement()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val clientRequestId = uuid("client_request_id")
    val fileName = varchar("file_name", 255)
    val status = enumerationByName<ImportStatus>("status", 20)
    val linkCount = integer("link_count")
    val folderCount = integer("folder_count")
    val skippedCount = integer("skipped_count")
    val failedCount = integer("failed_count")
    val reversedAt = timestamp("reversed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("bookmark_imports_owner_client_request_unique", ownerId, clientRequestId)
    }
}

fun ResultRow.toBookmarkImportDomain(): BookmarkImport =
    BookmarkImport(
        id = this[BookmarkImports.id],
        ownerId = this[BookmarkImports.ownerId],
        clientRequestId = this[BookmarkImports.clientRequestId],
        fileName = this[BookmarkImports.fileName],
        status = this[BookmarkImports.status],
        linkCount = this[BookmarkImports.linkCount],
        folderCount = this[BookmarkImports.folderCount],
        skippedCount = this[BookmarkImports.skippedCount],
        failedCount = this[BookmarkImports.failedCount],
        reversedAt = this[BookmarkImports.reversedAt]?.toKotlinInstant(),
        createdAt = this[BookmarkImports.createdAt].toKotlinInstant(),
        updatedAt = this[BookmarkImports.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(bookmarkImport: BookmarkImport) {
    this[BookmarkImports.ownerId] = bookmarkImport.ownerId
    this[BookmarkImports.clientRequestId] = bookmarkImport.clientRequestId
    this[BookmarkImports.fileName] = bookmarkImport.fileName
    this[BookmarkImports.status] = bookmarkImport.status
    this[BookmarkImports.linkCount] = bookmarkImport.linkCount
    this[BookmarkImports.folderCount] = bookmarkImport.folderCount
    this[BookmarkImports.skippedCount] = bookmarkImport.skippedCount
    this[BookmarkImports.failedCount] = bookmarkImport.failedCount
    this[BookmarkImports.reversedAt] = bookmarkImport.reversedAt?.toJavaInstant()
}

fun UpdateBuilder<*>.refreshBookmarkImportUpdatedAt() {
    this[BookmarkImports.updatedAt] = Clock.System.now().toJavaInstant()
}
