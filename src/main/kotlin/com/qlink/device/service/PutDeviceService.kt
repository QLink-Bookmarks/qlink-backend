package com.qlink.device.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken
import com.qlink.device.dto.PutDeviceRequest
import com.qlink.device.dto.PutDeviceResponse
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.user.repository.UserRepository

class PutDeviceService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    suspend fun putDevice(
        loginId: Long,
        request: PutDeviceRequest,
    ): PutDeviceResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.USER_NOT_FOUND)

            val deviceToken =
                deviceTokenRepository
                    .findByToken(request.token)
                    ?.let { deviceTokenRepository.update(it.changeOwner(loginId)) }
                    ?: DeviceToken(
                        userId = loginId,
                        platform = DevicePlatform.fromName(request.platform),
                        token = request.token,
                    ).let { deviceTokenRepository.insert(it) }

            PutDeviceResponse(id = deviceToken.id!!)
        }
}
