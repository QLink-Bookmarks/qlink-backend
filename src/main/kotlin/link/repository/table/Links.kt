package com.qlink.link.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Links : Table("links") {
  val id = long("id").autoIncrement()
  val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
  val folderId = reference("folder_id", Folders.id, onDelete = ReferenceOption.CASCADE)
  val url = text("url")
  val title = varchar("title", 300)
  val summary = text("summary").nullable()
  val oneLiner = text("one_liner").nullable()
  val tags = array<String>("tags", TextColumnType()).default(emptyList())
  val thumbnailUrl = text("thumbnail_url").nullable()
  val sourceType = varchar("source_type", 30)
  val reminderAt = timestampWithTimeZone("reminder_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")

  override val primaryKey = PrimaryKey(id)

  init {
    index("links_owner_id_idx", false, ownerId)
    index("links_folder_id_idx", false, folderId)
    index("links_tags_idx", false, tags, indexType = "GIN")
  }
}
