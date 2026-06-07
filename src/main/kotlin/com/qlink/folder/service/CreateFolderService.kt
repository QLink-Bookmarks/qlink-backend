package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.CreateFolderResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class CreateFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
) {
    suspend fun createFolder(
        loginId: Long,
        request: CreateFolderRequest,
    ): CreateFolderResponse =
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.FOLDER_OWNER_NOT_FOUND)
            folderRepository.existsByOwnerIdAndName(loginId, request.name).requireFalse(ErrorCode.FOLDER_DUPLICATE_NAME)

            val folder =
                Folder.create(
                    ownerId = loginId,
                    name = request.name,
                    emoji = request.emoji,
                    sharedAt = request.sharedAtOrNull(),
                )
            val created = folderRepository.insert(folder)

            if (created.sharedAt != null) {
                folderMemberRepository.insertIfAbsent(
                    FolderMember.owner(
                        folderId = created.id!!,
                        userId = loginId,
                        userName = user.nickname,
                        joinedAt = Clock.System.now(),
                    ),
                )
            }

            CreateFolderResponse(id = created.id!!)
        }

    private fun CreateFolderRequest.sharedAtOrNull(): kotlin.time.Instant? =
        if (isShared == true) {
            Clock.System.now()
        } else {
            null
        }
}
