package com.qlink.auth.service

import com.qlink.auth.client.AuthResourceClientRouter
import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.domain.RefreshToken
import com.qlink.auth.dto.AuthTokenResponse
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class SignInService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val authProviderRepository: AuthProviderRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authResourceClientRouter: AuthResourceClientRouter,
    private val authTokenService: AuthTokenService,
    private val randomUserNameGenerator: RandomUserNameGenerator,
) {
    suspend fun signIn(request: SignInRequest): AuthTokenResponse {
        val providerType = AuthProviderType.fromRequestName(request.provider)
        val resource =
            authResourceClientRouter.getResource(
                providerType = providerType,
                token = request.token,
                platform = request.platform,
            )

        return tx.required {
            val authProvider =
                authProviderRepository.findByProvider(
                    providerType = resource.providerType,
                    providerId = resource.providerId,
                )
            val user =
                if (authProvider == null) {
                    signUp(
                        providerType = resource.providerType,
                        providerId = resource.providerId,
                    )
                } else {
                    requireNotNull(userRepository.findById(authProvider.userId))
                }

            issueTokenResponse(user)
        }
    }

    private suspend fun signUp(
        providerType: AuthProviderType,
        providerId: String,
    ): User {
        val generatedName = randomUserNameGenerator.generate()
        val user =
            userRepository.insert(
                User(
                    username = generatedName.username,
                    nickname = generatedName.nickname,
                ),
            )
        val userId = requireNotNull(user.id)

        authProviderRepository.insert(
            AuthProvider(
                userId = userId,
                providerType = providerType,
                providerId = providerId,
            ),
        )

        return user
    }

    private suspend fun issueTokenResponse(user: User): AuthTokenResponse {
        val userId = requireNotNull(user.id)
        val now = Clock.System.now()
        val expiredAt = authTokenService.refreshTokenExpiredAt(now)
        val refreshToken = authTokenService.issueRefreshToken(userId = userId)

        refreshTokenRepository.insert(
            RefreshToken(
                userId = userId,
                familyId = authTokenService.verifyRefreshToken(refreshToken).familyId,
                token = refreshToken,
                issuedAt = now,
                expiredAt = expiredAt,
            ),
        )

        return AuthTokenResponse(
            accessToken =
                authTokenService.issueAccessToken(
                    userId = userId,
                    role = user.role,
                ),
            refreshToken = refreshToken,
        )
    }
}
