package com.qlink.ai.repository

import com.qlink.ai.domain.UserProvider

interface UserProviderRepository {
    suspend fun insert(userProvider: UserProvider): UserProvider

    suspend fun findByUserIdAndProviderId(
        userId: Long,
        providerId: Long,
    ): UserProvider?

    suspend fun findById(userProviderId: Long): UserProvider?

    suspend fun findAllByUserId(userId: Long): List<UserProvider>

    suspend fun update(userProvider: UserProvider): UserProvider
}
