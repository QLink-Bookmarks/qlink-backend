package com.qlink.folder.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue

class FolderDeletionOptions private constructor(
    val option: FolderDeleteOption,
    val targetFolderId: Long?,
) {
    companion object {
        fun from(
            onDelete: String?,
            targetFolderId: Long?,
        ): FolderDeletionOptions {
            val option = FolderDeleteOption.from(onDelete)
            if (option == FolderDeleteOption.MOVE) {
                (targetFolderId != null).requireTrue(ErrorCode.FOLDER_DELETE_TARGET_REQUIRED)
            } else {
                (targetFolderId == null).requireTrue(ErrorCode.COMMON_BAD_REQUEST)
            }
            return FolderDeletionOptions(option = option, targetFolderId = targetFolderId)
        }
    }
}
