package com.qlink.folder.repository.table

import com.qlink.folder.domain.Folder
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Folders : Table("folders") {
    val id = long("id").autoIncrement()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val emoji = varchar("emoji", 20).nullable()
    val sharedAt = timestamp("shared_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("folders_owner_id_idx", false, ownerId)
    }
}

fun ResultRow.toFolderDomain(): Folder =
    Folder(
        id = this[Folders.id],
        ownerId = this[Folders.ownerId],
        name = this[Folders.name],
        emoji = this[Folders.emoji],
        sharedAt = this[Folders.sharedAt]?.toKotlinInstant(),
        createdAt = this[Folders.createdAt].toKotlinInstant(),
        updatedAt = this[Folders.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(folder: Folder) {
    this[Folders.ownerId] = folder.ownerId
    this[Folders.name] = folder.name
    this[Folders.emoji] = folder.emoji
    this[Folders.sharedAt] = folder.sharedAt?.toJavaInstant()
}
