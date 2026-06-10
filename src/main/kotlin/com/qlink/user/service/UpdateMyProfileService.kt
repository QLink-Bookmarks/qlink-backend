package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.dto.UpdateMyProfileRequest
import com.qlink.user.repository.UserRepository

class UpdateMyProfileService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
) {
    suspend fun updateMyProfile(
        loginId: Long,
        request: UpdateMyProfileRequest,
    ) {
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            if (user.username != request.username) {
                userRepository
                    .existsByUsernameAndIdNot(
                        username = request.username,
                        userId = loginId,
                    ).requireFalse(ErrorCode.USER_USERNAME_DUPLICATED)
            }

            userRepository.update(
                user.changeProfile(
                    username = request.username,
                    nickname = request.nickname,
                    avatarUrl = request.avatarUrl,
                    avatarEmoji = request.avatarEmoji,
                ),
            )
        }
    }
}
