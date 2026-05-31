package com.qlink.ai.repository

import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.table.UserProviders
import com.qlink.ai.repository.table.fromDomain
import com.qlink.ai.repository.table.refreshUserProviderUpdatedAt
import com.qlink.ai.repository.table.toUserProviderDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbUserProviderRepository : UserProviderRepository {
    override suspend fun insert(userProvider: UserProvider): UserProvider =
        UserProviders
            .insertReturning { it.fromDomain(userProvider) }
            .single()
            .toUserProviderDomain()

    override suspend fun findByUserIdAndProviderId(
        userId: Long,
        providerId: Long,
    ): UserProvider? =
        UserProviders
            .selectAll()
            .where {
                (UserProviders.userId eq userId) and
                    (UserProviders.providerId eq providerId)
            }.singleOrNull()
            ?.toUserProviderDomain()

    override suspend fun findById(userProviderId: Long): UserProvider? =
        UserProviders
            .selectAll()
            .where { UserProviders.id eq userProviderId }
            .singleOrNull()
            ?.toUserProviderDomain()

    override suspend fun findAllByUserId(userId: Long): List<UserProvider> =
        UserProviders
            .selectAll()
            .where { UserProviders.userId eq userId }
            .orderBy(UserProviders.id to SortOrder.ASC)
            .map { it.toUserProviderDomain() }

    override suspend fun update(userProvider: UserProvider): UserProvider =
        UserProviders
            .updateReturning(where = { UserProviders.id eq userProvider.id!! }) {
                it.fromDomain(userProvider)
                it.refreshUserProviderUpdatedAt()
            }.single()
            .toUserProviderDomain()
}
