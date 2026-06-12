package com.qlink.user.repository

import com.qlink.user.domain.User

interface UserRepository {
    suspend fun emptyById(userId: Long): Boolean

    suspend fun findById(userId: Long): User?

    suspend fun existsByUsernameAndIdNot(
        username: String,
        userId: Long,
    ): Boolean

    suspend fun insert(user: User): User

    suspend fun update(user: User): User

    suspend fun deleteById(userId: Long)
}
