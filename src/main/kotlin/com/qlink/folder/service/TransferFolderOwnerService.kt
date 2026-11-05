package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireTrue
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository

class TransferFolderOwnerService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
) {
    suspend fun transferOwner(
        loginId: Long,
        folderId: Long,
        ownerId: Long,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_TRANSFER_CALLER_NOT_FOUND)
            val folder =
                folderRepository.findAllByIdsForUpdate(listOf(folderId)).singleOrNull()
                    ?: throw BusinessException(ErrorCode.FOLDER_TRANSFER_FOLDER_NOT_FOUND)
            folder.validateOwner(loginId)
            folder.validateTransferTo(ownerId)

            folderMemberRepository.findByFolderIdAndUserIdForUpdate(
                folderId = folderId,
                userId = ownerId,
            ) ?: throw BusinessException(ErrorCode.FOLDER_TRANSFER_MEMBER_NOT_FOUND)
            folderRepository
                .existsByOwnerIdAndNameAndIdNot(
                    ownerId = ownerId,
                    name = folder.name,
                    folderId = folderId,
                ).requireFalse(ErrorCode.FOLDER_TRANSFER_DUPLICATE_NAME)

            folderRepository.update(folder.delegateTo(ownerId))
            // The locked member must still exist; retain the affected-row check as a persistence assertion.
            (
                folderMemberRepository.updateRole(
                    folderId = folderId,
                    userId = ownerId,
                    role = MemberRole.OWNER,
                ) == 1
            ).requireTrue(ErrorCode.FOLDER_TRANSFER_MEMBER_NOT_FOUND)
            folderMemberRepository.deleteByFolderIdAndUserId(
                folderId = folderId,
                userId = loginId,
            )
        }
    }
}
