package com.qlink.foldermember.repository.table

import com.qlink.folder.repository.table.Folders
import com.qlink.foldermember.domain.FolderMember
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object FolderMembers : Table("folder_members") {
    val folderId = reference("folder_id", Folders.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val userName = varchar("user_name", 50)
    val role = varchar("role", 15)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(folderId, userId)

    init {
        index("folder_members_user_id_idx", false, userId)
    }
}

fun ResultRow.toFolderMemberDomain(): FolderMember =
    FolderMember(
        folderId = this[FolderMembers.folderId],
        userId = this[FolderMembers.userId],
        userName = this[FolderMembers.userName],
        role = this[FolderMembers.role],
        joinedAt = this[FolderMembers.joinedAt].toKotlinInstant(),
        createdAt = this[FolderMembers.createdAt].toKotlinInstant(),
        updatedAt = this[FolderMembers.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(folderMember: FolderMember) {
    this[FolderMembers.folderId] = folderMember.folderId
    this[FolderMembers.userId] = folderMember.userId
    this[FolderMembers.userName] = folderMember.userName
    this[FolderMembers.role] = folderMember.role
    this[FolderMembers.joinedAt] = folderMember.joinedAt.toJavaInstant()
}
