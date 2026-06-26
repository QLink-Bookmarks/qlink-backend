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
    ): Folder =
        (folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND))
            .takeIf { it.isOwnedBy(userId) || canMemberWrite(it, userId) }
            ?: throw BusinessException(ErrorCode.LINK_FOLDER_ACCESS_DENIED)

    private suspend fun canMemberWrite(
        folder: Folder,
        userId: Long,
    ): Boolean =
        folder.sharedAt != null &&
            folderMemberRepository.findByFolderIdAndUserId(folder.id!!, userId)?.canWriteLink() == true
}
