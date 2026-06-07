package com.qlink.folder.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.SecurityConfig
import com.qlink.folder.dto.AcceptFolderInvitationRequest
import com.qlink.folder.dto.AcceptFolderInvitationResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class AcceptFolderInvitationService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val userRepository: UserRepository,
    private val securityConfig: SecurityConfig,
) {
    suspend fun acceptInvitation(
        loginId: Long,
        folderId: Long,
        request: AcceptFolderInvitationRequest,
    ): AcceptFolderInvitationResponse =
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            val tokenFolderId = verifyInvitation(request.invitation)
            if (tokenFolderId != folderId) {
                throw BusinessException(ErrorCode.FOLDER_INVITATION_INVALID)
            }

            folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.FOLDER_NOT_FOUND)

            folderMemberRepository.insertIfAbsent(
                FolderMember.member(
                    folderId = folderId,
                    userId = loginId,
                    userName = user.nickname,
                    joinedAt = Clock.System.now(),
                ),
            )

            AcceptFolderInvitationResponse(folderId = folderId)
        }

    private fun verifyInvitation(invitation: String): Long {
        val payload =
            try {
                JWT
                    .require(Algorithm.HMAC256(securityConfig.jwtSecret))
                    .build()
                    .verify(invitation)
            } catch (exception: TokenExpiredException) {
                throw BusinessException(ErrorCode.FOLDER_INVITATION_EXPIRED, exception)
            } catch (exception: JWTVerificationException) {
                throw BusinessException(ErrorCode.FOLDER_INVITATION_INVALID, exception)
            }

        return payload.subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.FOLDER_INVITATION_INVALID)
    }
}
