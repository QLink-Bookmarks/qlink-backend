package com.qlink.ai.repository

import com.qlink.ai.domain.UserProvider
import com.qlink.auth.domain.Role

interface UserProviderRepository {
    suspend fun insert(userProvider: UserProvider): UserProvider

    suspend fun save(userProvider: UserProvider): UserProvider

    suspend fun findByUserIdAndProviderId(
        userId: Long,
        providerId: Long,
    ): UserProvider?

    suspend fun findById(userProviderId: Long): UserProvider?

    suspend fun findAllByUserId(userId: Long): List<UserProvider>

    suspend fun findAllByRole(userProviderRole: Role): List<UserProvider>

    suspend fun update(userProvider: UserProvider): UserProvider
}
