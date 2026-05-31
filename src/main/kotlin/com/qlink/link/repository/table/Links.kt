package com.qlink.link.repository.table

import com.qlink.ai.repository.table.AvailableModels
import com.qlink.folder.repository.table.Folders
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
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
    val searchText = text("search_text").default("")
    val thumbnailUrl = text("thumbnail_url").nullable()
    val sourceType = enumerationByName<SourceType>("source_type", 30)
    val status = enumerationByName<LinkStatus>("status", 1).default(LinkStatus.C)
    val workModelId =
        reference("work_model_id", AvailableModels.id, onDelete = ReferenceOption.SET_NULL)
            .nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("links_owner_id_idx", false, ownerId)
        index("links_folder_id_idx", false, folderId)
        index("links_status_idx", false, status)
        index("links_work_model_id_idx", false, workModelId)
        index("links_tags_idx", false, tags, indexType = "GIN")
    }
}

fun ResultRow.toLinkDomain(): Link =
    Link(
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
        status = this[Links.status],
        workModelId = this[Links.workModelId],
        createdAt = this[Links.createdAt].toKotlinInstant(),
        updatedAt = this[Links.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(link: Link) {
    this[Links.ownerId] = link.ownerId
    this[Links.folderId] = link.folderId
    this[Links.url] = link.url
    this[Links.title] = link.title
    this[Links.summary] = link.summary
    this[Links.memo] = link.memo
    this[Links.tags] = link.tags
    this[Links.searchText] = link.searchText()
    this[Links.thumbnailUrl] = link.thumbnailUrl
    this[Links.sourceType] = link.sourceType
    this[Links.status] = link.status
    this[Links.workModelId] = link.workModelId
}

fun UpdateBuilder<*>.refreshUpdatedAt() {
    this[Links.updatedAt] = Clock.System.now().toJavaInstant()
}

private fun Link.searchText(): String =
    listOf(
        title,
        url,
        tags.joinToString(" "),
        summary.orEmpty(),
        memo.orEmpty(),
    ).joinToString(" ")
