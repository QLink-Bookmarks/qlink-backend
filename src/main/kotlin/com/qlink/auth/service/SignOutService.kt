package com.qlink.auth.service

import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.repository.UserRepository

class SignOutService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    suspend fun signOut(
        loginId: Long,
        refreshToken: String?,
    ) {
        tx.required {
            userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            refreshToken
                ?.takeIf { it.isNotBlank() }
                ?.let { refreshTokenRepository.deleteByUserIdAndToken(userId = loginId, token = it) }
        }
    }
}
