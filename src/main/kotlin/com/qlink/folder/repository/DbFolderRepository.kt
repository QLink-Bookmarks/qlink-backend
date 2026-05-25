package com.qlink.folder.repository

import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.table.Folders
import com.qlink.folder.repository.table.fromDomain
import com.qlink.folder.repository.table.refreshUpdatedAt
import com.qlink.folder.repository.table.toFolderDomain
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbFolderRepository : FolderRepository {
    override suspend fun findById(id: Long): Folder? =
        Folders
            .selectAll()
            .where { Folders.id eq id }
            .singleOrNull()
            ?.toFolderDomain()

    override suspend fun existsByOwnerIdAndName(
        ownerId: Long,
        name: String,
    ): Boolean =
        Folders
            .select(Folders.id)
            .where { (Folders.ownerId eq ownerId) and (Folders.name eq name) }
            .empty()
            .not()

    override suspend fun existsByOwnerIdAndNameAndIdNot(
        ownerId: Long,
        name: String,
        folderId: Long,
    ): Boolean =
        Folders
            .select(Folders.id)
            .where {
                (Folders.ownerId eq ownerId) and
                    (Folders.name eq name) and
                    (Folders.id neq folderId)
            }.empty()
            .not()

    override suspend fun insert(folder: Folder): Folder = Folders.insertReturning { it.fromDomain(folder) }.single().toFolderDomain()

    override suspend fun update(folder: Folder): Folder =
        Folders
            .updateReturning(where = { Folders.id eq folder.id!! }) {
                it.fromDomain(folder)
                it.refreshUpdatedAt()
            }.single()
            .toFolderDomain()
}
