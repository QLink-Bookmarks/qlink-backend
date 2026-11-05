package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository

class DeleteAccountService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
) {
    suspend fun deleteAccount(loginId: Long) {
        tx.required {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            val ownedFolderIds = folderRepository.findAllByOwnerId(loginId).map { it.id!! }
            folderRepository
                .findAllByIdsForUpdate(ownedFolderIds)
                .filter { it.isOwnedBy(loginId) && it.isShared }
                .forEach { folder -> handOverSharedFolder(folder = folder, loginId = loginId) }

            userRepository.deleteById(loginId)
        }
    }

    private suspend fun handOverSharedFolder(
        folder: Folder,
        loginId: Long,
    ) {
        val folderId = requireNotNull(folder.id)
        val nextOwner =
            folderMemberRepository
                .findAllByFolderIdOrderByJoinedAtAsc(folderId)
                .firstOrNull { it.userId != loginId }

        if (nextOwner == null) {
            folderRepository.deleteById(folderId)
            return
        }

        folderRepository.update(folder.delegateTo(nextOwner.userId))
        folderMemberRepository.updateRole(folderId, nextOwner.userId, MemberRole.OWNER)
    }
}
