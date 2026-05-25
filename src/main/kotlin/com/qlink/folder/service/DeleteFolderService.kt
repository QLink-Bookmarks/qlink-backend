package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class DeleteFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
) {
    suspend fun deleteFolder(
        loginId: Long,
        folderId: Long,
        onDelete: String?,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_OWNER_NOT_FOUND)

            val folder = folderRepository.findById(folderId) ?: return@required
            folder.validateOwner(loginId)

            when (FolderDeleteOption.from(onDelete)) {
                FolderDeleteOption.NULL -> linkRepository.detachFolder(folderId)
                FolderDeleteOption.CASCADE -> linkRepository.deleteAllByFolderId(folderId)
            }

            folderRepository.deleteById(folderId)
        }
    }
}

enum class FolderDeleteOption {
    CASCADE,
    NULL,
    ;

    companion object {
        fun from(value: String?): FolderDeleteOption =
            when (value?.lowercase() ?: "null") {
                "cascade" -> CASCADE
                "null" -> NULL
                else -> throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
    }
}
