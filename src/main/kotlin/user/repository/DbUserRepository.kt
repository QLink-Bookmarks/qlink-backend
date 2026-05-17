package com.qlink.user.repository

import com.qlink.user.domain.User
import com.qlink.user.repository.table.Users
import com.qlink.user.repository.table.fromDomain
import com.qlink.user.repository.table.toUserDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select

class DbUserRepository() : UserRepository {

    override suspend fun emptyById(userId: Long): Boolean {
        return Users.select(Users.id).where { Users.id eq userId }.empty()
    }

    override suspend fun insert(user: User): User {
        return Users.insertReturning { it.fromDomain(user) }.single().toUserDomain()
    }

}