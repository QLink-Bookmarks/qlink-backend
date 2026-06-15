package com.qlink.auth.client

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.AppleConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * 애플은 다른 제공자와 달리 리소스 API 호출 대신, 클라이언트가 넘긴 id_token(JWT)을
 * 애플 공개키(JWKS)로 검증한 뒤 `sub` 클레임을 providerId로 추출한다.
 */
class AppleAuthResourceClient(
    private val httpClient: HttpClient,
    private val appleConfig: AppleConfig,
) : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.APPLE

    override suspend fun getResource(token: String): AuthResource {
        val keyId =
            runCatching { JWT.decode(token).keyId }
                .getOrElse { throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it) }
                ?: throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)

        val publicKey = fetchPublicKey(keyId)

        val verified =
            runCatching {
                JWT
                    .require(Algorithm.RSA256(SingleKeyProvider(publicKey)))
                    .withIssuer(APPLE_ISSUER)
                    .build()
                    .verify(token)
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it)
            }

        if (appleConfig.clientIds.none { it in verified.audience.orEmpty() }) {
            throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)
        }

        val subject =
            verified.subject?.takeIf(String::isNotBlank)
                ?: throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)

        return AuthResource(
            providerType = providerType,
            providerId = subject,
        )
    }

    private suspend fun fetchPublicKey(keyId: String): RSAPublicKey {
        val response =
            runCatching { httpClient.get(APPLE_PUBLIC_KEYS_URL) }
                .getOrElse { throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it) }

        if (!response.status.isSuccess()) {
            throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)
        }

        val keys =
            runCatching { response.body<ApplePublicKeys>() }
                .getOrElse { throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it) }

        val jwk =
            keys.keys.firstOrNull { it.kid == keyId }
                ?: throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)

        return runCatching { jwk.toRsaPublicKey() }
            .getOrElse { throw BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, it) }
    }

    private companion object {
        const val APPLE_ISSUER = "https://appleid.apple.com"
        const val APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys"
    }
}

private class SingleKeyProvider(
    private val publicKey: RSAPublicKey,
) : RSAKeyProvider {
    override fun getPublicKeyById(keyId: String?): RSAPublicKey = publicKey

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}

@Serializable
private data class ApplePublicKeys(
    val keys: List<ApplePublicKey>,
)

@Serializable
private data class ApplePublicKey(
    val kid: String,
    val n: String,
    val e: String,
) {
    fun toRsaPublicKey(): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(n))
        val exponent = BigInteger(1, decoder.decode(e))
        return KeyFactory
            .getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
    }
}
