package com.qlink.device.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.user.repository.UserRepository

class DeleteDeviceService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    suspend fun deleteDevice(
        loginId: Long,
        token: String,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)

            deviceTokenRepository
                .findByToken(token)
                ?.also { it.validateOwner(loginId) }
                ?.let { deviceTokenRepository.deleteByToken(token) }
        }
    }
}
