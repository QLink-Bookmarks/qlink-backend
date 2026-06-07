package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.dto.UpdateFolderResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class UpdateFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
) {
    suspend fun updateFolder(
        loginId: Long,
        folderId: Long,
        request: UpdateFolderRequest,
    ): UpdateFolderResponse =
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.FOLDER_OWNER_NOT_FOUND)

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
                    sharedAt = request.sharedAtOrExisting(folder.sharedAt),
                )
            val saved = folderRepository.update(updated)

            if (saved.sharedAt != null && folderMemberRepository.existsByFolderId(folderId).not()) {
                folderMemberRepository.insertIfAbsent(
                    FolderMember.owner(
                        folderId = folderId,
                        userId = loginId,
                        userName = user.nickname,
                        joinedAt = Clock.System.now(),
                    ),
                )
            }

            UpdateFolderResponse(id = saved.id!!)
        }

    private fun UpdateFolderRequest.sharedAtOrExisting(current: kotlin.time.Instant?): kotlin.time.Instant? =
        when (isShared) {
            true -> current ?: Clock.System.now()
            false -> null
            null -> current
        }
}
