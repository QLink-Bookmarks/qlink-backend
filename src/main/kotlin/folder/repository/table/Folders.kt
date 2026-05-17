package com.qlink.folder.repository.table

import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

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
