package com.qlink.folder.repository

import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.table.Folders
import com.qlink.folder.repository.table.fromDomain
import com.qlink.folder.repository.table.toFolderDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbFolderRepository : FolderRepository {
    override suspend fun findById(id: Long): Folder? =
        Folders
            .selectAll()
            .where { Folders.id eq id }
            .singleOrNull()
            ?.toFolderDomain()

    override suspend fun insert(folder: Folder): Folder = Folders.insertReturning { it.fromDomain(folder) }.single().toFolderDomain()
}
