package com.qlink.bookmarkimport.repository.table

import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFailureReason
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toKotlinInstant

object BookmarkImportFailures : Table("bookmark_import_failures") {
    val id = long("id").autoIncrement()
    val importId = reference("import_id", BookmarkImports.id, onDelete = ReferenceOption.CASCADE)
    val title = text("title")
    val url = text("url")
    val reason = enumerationByName<ImportFailureReason>("reason", 20)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("bookmark_import_failures_import_id_idx", false, importId)
    }
}

fun ResultRow.toImportFailureDomain(): ImportFailure =
    ImportFailure(
        id = this[BookmarkImportFailures.id],
        importId = this[BookmarkImportFailures.importId],
        title = this[BookmarkImportFailures.title],
        url = this[BookmarkImportFailures.url],
        reason = this[BookmarkImportFailures.reason],
        createdAt = this[BookmarkImportFailures.createdAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(failure: ImportFailure) {
    this[BookmarkImportFailures.importId] = failure.importId
    this[BookmarkImportFailures.title] = failure.title
    this[BookmarkImportFailures.url] = failure.url
    this[BookmarkImportFailures.reason] = failure.reason
}
