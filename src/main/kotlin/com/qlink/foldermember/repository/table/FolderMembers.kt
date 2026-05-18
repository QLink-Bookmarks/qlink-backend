package com.qlink.foldermember.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object FolderMembers : Table("folder_members") {
    val folderId = reference("folder_id", Folders.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 15)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(folderId, userId)

    init {
        index("folder_members_user_id_idx", false, userId)
    }
}
