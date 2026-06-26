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

        if (folder.isOwnedBy(userId)) {
            return folder
        }

        val isWritableMember =
            folder.sharedAt != null &&
                folderMemberRepository.findByFolderIdAndUserId(folderId, userId)?.canWriteLink() == true
        if (isWritableMember) {
            return folder
        }

        throw BusinessException(ErrorCode.LINK_FOLDER_ACCESS_DENIED)
    }
}
