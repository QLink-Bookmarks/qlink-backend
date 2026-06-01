package com.qlink.user.repository

import com.qlink.user.domain.User

interface UserRepository {
    suspend fun emptyById(userId: Long): Boolean

    suspend fun findById(userId: Long): User?

    suspend fun insert(user: User): User
}
