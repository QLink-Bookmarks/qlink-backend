package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.dto.GetFolderMemberResponse
import com.qlink.folder.dto.GetFolderMembersResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository

class GetFolderMembersService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getFolderMembers(
        loginId: Long,
        folderId: Long,
    ): GetFolderMembersResponse =
        tx.readOnly {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.FOLDER_MEMBER_OWNER_NOT_FOUND)
            val folder = folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.FOLDER_MEMBER_FOLDER_NOT_FOUND)
            folder.sharedAt ?: throw BusinessException(ErrorCode.FOLDER_MEMBER_NOT_SHARED_FOLDER)

            val memberQueries = folderMemberRepository.findAllByFolderIdOrderByJoinedAtDesc(folderId)
            memberQueries.any { it.userId == loginId }.requireTrue(ErrorCode.FOLDER_MEMBER_ACCESS_DENIED)

            val owner = userRepository.findById(folder.ownerId) ?: throw BusinessException(ErrorCode.FOLDER_MEMBER_OWNER_NOT_FOUND)
            val members =
                memberQueries.map {
                    GetFolderMemberResponse(
                        userId = it.userId,
                        role = it.role,
                        userNickname = it.userNickname,
                        avatarUrl = it.avatarUrl,
                        avatarEmoji = it.avatarEmoji,
                    )
                }

            GetFolderMembersResponse(
                ownerId = folder.ownerId,
                ownerNickname = owner.nickname,
                ownerAvatarUrl = owner.avatarUrl,
                ownerAvatarEmoji = owner.avatarEmoji,
                members = members,
            )
        }
}
