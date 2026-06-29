package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.dto.UpdateMyAgreementsRequest
import com.qlink.user.repository.UserRepository

class UpdateMyAgreementsService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
) {
    suspend fun updateMyAgreements(
        loginId: Long,
        request: UpdateMyAgreementsRequest,
    ) {
        tx.required {
            val user = userRepository.findById(loginId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            userRepository.update(
                user.changeAgreements(
                    allowsPrivacy = request.allowsPrivacy,
                    allowsAiUsage = request.allowsAiUsage,
                ),
            )
        }
    }
}
