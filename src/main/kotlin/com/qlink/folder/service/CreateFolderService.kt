package com.qlink.folder.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.CreateFolderResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.user.repository.UserRepository
import java.time.Instant
import kotlin.time.toKotlinInstant

class CreateFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val userRepository: UserRepository,
) {
    suspend fun createFolder(
        loginId: Long,
        request: CreateFolderRequest,
    ): CreateFolderResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_OWNER_NOT_FOUND)
            folderRepository.existsByOwnerIdAndName(loginId, request.name).requireFalse(ErrorCode.FOLDER_DUPLICATE_NAME)

            val folder =
                Folder.create(
                    ownerId = loginId,
                    name = request.name,
                    emoji = request.emoji,
                    sharedAt = request.sharedAtOrNull(),
                )
            val created = folderRepository.insert(folder)

            CreateFolderResponse(id = created.id!!)
        }

    private fun CreateFolderRequest.sharedAtOrNull(): kotlin.time.Instant? =
        if (isShared == true) {
            Instant.now().toKotlinInstant()
        } else {
            null
        }
}
