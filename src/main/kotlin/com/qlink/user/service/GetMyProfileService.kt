package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.domain.User
import com.qlink.user.dto.GetMyProfileResponse
import com.qlink.user.repository.UserRepository

class GetMyProfileService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
) {
    suspend fun getMyProfile(loginId: Long): GetMyProfileResponse =
        tx.readOnly {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            user.toResponse()
        }

    private fun User.toResponse(): GetMyProfileResponse =
        GetMyProfileResponse(
            id = id!!,
            username = username,
            nickname = nickname,
            role = role,
            avatarUrl = avatarUrl,
            avatarEmoji = avatarEmoji,
            allowsPrivacy = allowsPrivacy,
            allowsAiUsage = allowsAiUsage,
        )
}
