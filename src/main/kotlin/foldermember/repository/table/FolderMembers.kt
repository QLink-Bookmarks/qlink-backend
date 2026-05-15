package com.qlink.foldermember.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object FolderMembers : Table("folder_members") {
    val folderId = reference("folder_id", Folders.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 15)
    val joinedAt = timestampWithTimeZone("joined_at")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(folderId, userId)

    init {
        index("folder_members_user_id_idx", false, userId)
    }
}
