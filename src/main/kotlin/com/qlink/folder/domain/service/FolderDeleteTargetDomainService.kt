package com.qlink.folder.domain.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.folder.domain.Folder
import com.qlink.foldermember.domain.FolderMember

object FolderDeleteTargetDomainService {
    fun validateWritable(
        target: Folder,
        member: FolderMember?,
        loginId: Long,
    ) {
        val memberCanWrite =
            member
                ?.takeIf { it.folderId == target.id && it.userId == loginId }
                ?.canWriteLink() == true
        target
            .isLinkWritableBy(userId = loginId, memberCanWrite = memberCanWrite)
            .requireTrue(ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED)
    }
}
