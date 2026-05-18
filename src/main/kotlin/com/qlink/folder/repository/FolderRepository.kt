package com.qlink.folder.repository

import com.qlink.folder.domain.Folder

interface FolderRepository {
    suspend fun findById(id: Long): Folder?

    suspend fun insert(folder: Folder): Folder
}
