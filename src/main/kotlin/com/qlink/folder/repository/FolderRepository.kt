package com.qlink.folder.repository

import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.SearchFoldersQuery

interface FolderRepository {
    suspend fun findById(id: Long): Folder?

    suspend fun existsByOwnerIdAndName(
        ownerId: Long,
        name: String,
    ): Boolean

    suspend fun existsByOwnerIdAndNameAndIdNot(
        ownerId: Long,
        name: String,
        folderId: Long,
    ): Boolean

    suspend fun insert(folder: Folder): Folder

    suspend fun search(
        ownerId: Long,
        query: String?,
        order: FolderSearchOrder,
        cursor: FolderSearchCursor?,
        size: Int,
    ): List<SearchFoldersQuery>

    suspend fun update(folder: Folder): Folder

    suspend fun deleteById(folderId: Long)
}
