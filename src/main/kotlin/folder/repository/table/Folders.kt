package com.qlink.folder.repository.table

import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Folders : Table("folders") {
    val id = long("id").autoIncrement()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val emoji = varchar("emoji", 20).nullable()
    val sharedAt = timestampWithTimeZone("shared_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("folders_owner_id_idx", false, ownerId)
    }
}
