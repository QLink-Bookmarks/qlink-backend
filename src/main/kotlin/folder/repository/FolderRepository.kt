package com.qlink.folder.repository

interface FolderRepository {

    suspend fun emptyById(id: Long): Boolean

}