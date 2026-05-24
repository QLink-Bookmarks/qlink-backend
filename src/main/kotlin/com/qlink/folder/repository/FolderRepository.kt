package com.qlink.folder.repository

import com.qlink.folder.domain.Folder

interface FolderRepository {
    suspend fun findById(id: Long): Folder?

    suspend fun existsByOwnerIdAndName(
        ownerId: Long,
        name: String,
    ): Boolean

    suspend fun insert(folder: Folder): Folder
}
