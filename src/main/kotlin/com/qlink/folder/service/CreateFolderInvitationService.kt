package com.qlink.folder.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.SecurityConfig
import com.qlink.folder.dto.CreateFolderInvitationRequest
import com.qlink.folder.dto.CreateFolderInvitationResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.user.repository.UserRepository
import java.time.Instant
import java.util.Date

class CreateFolderInvitationService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val userRepository: UserRepository,
    private val securityConfig: SecurityConfig,
) {
    suspend fun createInvitation(
        loginId: Long,
        folderId: Long,
        request: CreateFolderInvitationRequest,
    ): CreateFolderInvitationResponse =
        tx.readOnly {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.FOLDER_OWNER_NOT_FOUND)

            val folder = folderRepository.findById(folderId) ?: throw BusinessException(ErrorCode.FOLDER_NOT_FOUND)
            folder.validateOwner(loginId)
            if (folder.sharedAt == null) {
                throw BusinessException(ErrorCode.FOLDER_NOT_SHARED)
            }

            val builder =
                JWT
                    .create()
                    .withSubject(folderId.toString())

            request.durationDays
                ?.takeIf { it > 0 }
                ?.let { days ->
                    builder.withExpiresAt(Date.from(Instant.now().plusSeconds(days * 24L * 60L * 60L)))
                }

            CreateFolderInvitationResponse(
                invitation = builder.sign(Algorithm.HMAC256(securityConfig.jwtSecret)),
            )
        }
}
