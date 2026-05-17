package com.qlink.link.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Links : Table("links") {
    val id = long("id").autoIncrement()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val folderId =
        reference("folder_id", Folders.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val url = text("url")
    val title = varchar("title", 300)
    val summary = text("summary").nullable()
    val memo = text("memo").nullable()
    val tags = array<String>("tags", TextColumnType()).default(emptyList())
    val thumbnailUrl = text("thumbnail_url").nullable()
    val sourceType = enumerationByName<SourceType>("source_type", 30)
    val reminderAt = timestamp("reminder_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("links_owner_id_idx", false, ownerId)
        index("links_folder_id_idx", false, folderId)
        index("links_tags_idx", false, tags, indexType = "GIN")
    }

}

fun ResultRow.toLinkDomain(): Link {
    return Link(
        id = this[Links.id],
        ownerId = this[Links.ownerId],
        folderId = this[Links.folderId],
        url = this[Links.url],
        title = this[Links.title],
        summary = this[Links.summary],
        memo = this[Links.memo],
        tags = this[Links.tags],
        thumbnailUrl = this[Links.thumbnailUrl],
        sourceType = this[Links.sourceType],
        reminderAt = this[Links.reminderAt]?.toKotlinInstant(),
        createdAt = this[Links.createdAt].toKotlinInstant(),
        updatedAt = this[Links.updatedAt].toKotlinInstant(),
    )
}

fun UpdateBuilder<*>.fromDomain(link: Link) {
    this[Links.ownerId] = link.ownerId
    this[Links.folderId] = link.folderId
    this[Links.url] = link.url
    this[Links.title] = link.title
    this[Links.summary] = link.summary
    this[Links.memo] = link.memo
    this[Links.tags] = link.tags
    this[Links.thumbnailUrl] = link.thumbnailUrl
    this[Links.sourceType] = link.sourceType
    this[Links.reminderAt] = link.reminderAt?.toJavaInstant()
    this[Links.updatedAt] = Clock.System.now().toJavaInstant()
}
