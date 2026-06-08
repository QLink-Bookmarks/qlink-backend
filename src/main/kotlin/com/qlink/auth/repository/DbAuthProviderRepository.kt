package com.qlink.auth.repository

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.repository.table.AuthProviders
import com.qlink.auth.repository.table.fromDomain
import com.qlink.auth.repository.table.toAuthProviderDomain
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbAuthProviderRepository : AuthProviderRepository {
    override suspend fun findByProvider(
        providerType: AuthProviderType,
        providerId: String,
    ): AuthProvider? =
        AuthProviders
            .selectAll()
            .where {
                (AuthProviders.providerType eq providerType.name) and
                    (AuthProviders.providerId eq providerId)
            }.singleOrNull()
            ?.toAuthProviderDomain()

    override suspend fun insert(authProvider: AuthProvider): AuthProvider =
        AuthProviders
            .insertReturning {
                it.fromDomain(authProvider)
            }.single()
            .toAuthProviderDomain()
}
