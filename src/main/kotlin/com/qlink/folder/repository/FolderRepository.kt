package com.qlink.folder.repository

import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.SearchFoldersQuery
import kotlin.time.Instant

interface FolderRepository {
    suspend fun findById(id: Long): Folder?

    suspend fun findAllByIds(ids: List<Long>): List<Folder>

    suspend fun findAllByIdsForUpdate(ids: List<Long>): List<Folder>

    suspend fun findAllByOwnerId(ownerId: Long): List<Folder>

    suspend fun findCreatedAtById(id: Long): Instant?

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

    suspend fun insertAll(folders: List<Folder>): List<Folder>

    suspend fun search(
        ownerId: Long,
        query: String?,
        order: FolderSearchOrder,
        cursor: FolderSearchCursor?,
        size: Int,
    ): List<SearchFoldersQuery>

    suspend fun update(folder: Folder): Folder

    suspend fun deleteById(folderId: Long)

    suspend fun deleteAllByIds(folderIds: List<Long>)
}
