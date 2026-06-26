package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository

class FolderAccessValidator(
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
) {
    suspend fun validateWritable(
        folderId: Long,
        userId: Long,
    ): Folder {
        val folder =
            folderRepository.findById(folderId)
                ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)

        if (folder.ownerId == userId) {
            return folder
        }

        if (folder.sharedAt != null && folderMemberRepository.existsByFolderIdAndUserId(folderId, userId)) {
            return folder
        }

        throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)
    }
}
