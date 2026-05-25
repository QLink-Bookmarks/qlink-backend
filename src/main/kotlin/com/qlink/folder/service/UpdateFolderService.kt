package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.dto.UpdateFolderResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.user.repository.UserRepository

class UpdateFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val userRepository: UserRepository,
) {
    suspend fun updateFolder(
        loginId: Long,
        folderId: Long,
        request: UpdateFolderRequest,
    ): UpdateFolderResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_OWNER_NOT_FOUND)

            val folder = folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.FOLDER_NOT_FOUND)
            folder.validateOwner(loginId)

            folderRepository
                .existsByOwnerIdAndNameAndIdNot(
                    ownerId = loginId,
                    name = request.name,
                    folderId = folderId,
                ).requireFalse(ErrorCode.FOLDER_DUPLICATE_NAME)

            val updated =
                folder.update(
                    name = request.name,
                    emoji = request.emoji,
                )

            UpdateFolderResponse(id = folderRepository.update(updated).id!!)
        }
}
