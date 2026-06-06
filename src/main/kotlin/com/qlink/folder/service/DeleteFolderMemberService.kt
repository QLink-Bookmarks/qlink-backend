package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository

class DeleteFolderMemberService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
) {
    suspend fun deleteMember(
        loginId: Long,
        folderId: Long,
        memberId: Long,
    ) {
        tx.required {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.FOLDER_OWNER_NOT_FOUND)
            val folder = folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)

            if (loginId != memberId && folder.ownerId != loginId) {
                throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)
            }

            folderMemberRepository.deleteByFolderIdAndUserId(
                folderId = folderId,
                userId = memberId,
            )
        }
    }
}
