package com.qlink.auth.repository.table

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.time.toKotlinInstant

fun ResultRow.toAuthProviderDomain(): AuthProvider =
    AuthProvider(
        id = this[AuthProviders.id],
        userId = this[AuthProviders.userId],
        providerType = AuthProviderType.valueOf(this[AuthProviders.providerType]),
        providerId = this[AuthProviders.providerId],
        createdAt = this[AuthProviders.createdAt].toKotlinInstant(),
        updatedAt = this[AuthProviders.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(authProvider: AuthProvider) {
    this[AuthProviders.userId] = authProvider.userId
    this[AuthProviders.providerType] = authProvider.providerType.name
    this[AuthProviders.providerId] = authProvider.providerId
}
