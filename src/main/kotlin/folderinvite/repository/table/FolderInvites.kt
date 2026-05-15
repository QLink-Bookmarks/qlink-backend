package com.qlink.folderinvite.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object FolderInvites : Table("folder_invites") {
    val id = long("id").autoIncrement()
    val folderId = reference("folder_id", Folders.id, onDelete = ReferenceOption.CASCADE)
    val inviterId = reference("inviter_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 64)
    val expiresAt = timestampWithTimeZone("expires_at")
    val acceptedAt = timestampWithTimeZone("accepted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("folder_invites_token_unique", token)
        index("folder_invites_folder_id_idx", false, folderId)
        index("folder_invites_inviter_id_idx", false, inviterId)
    }
}
