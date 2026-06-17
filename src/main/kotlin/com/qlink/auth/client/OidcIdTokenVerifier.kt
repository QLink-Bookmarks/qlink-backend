package com.qlink.auth.client

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * OpenID Connect id_token(JWT)을 제공자 공개키(JWKS)로 검증하고 `sub`를 추출한다.
 * 애플, 구글(네이티브)처럼 리소스 API 호출 대신 id_token을 직접 검증하는 제공자가 공유한다.
 */
class OidcIdTokenVerifier(
    private val httpClient: HttpClient,
    private val jwksUrl: String,
    private val issuers: List<String>,
) {
    suspend fun verifyAndGetSubject(
        idToken: String,
        allowedAudiences: List<String>,
    ): String {
        val keyId =
            runCatching { JWT.decode(idToken).keyId }
                .getOrElse { throw clientFailed(it) }
                ?: throw clientFailed()

        val publicKey = fetchPublicKey(keyId)

        val verified =
            runCatching {
                JWT
                    .require(Algorithm.RSA256(SingleKeyProvider(publicKey)))
                    .withIssuer(*issuers.toTypedArray())
                    .build()
                    .verify(idToken)
            }.getOrElse { throw clientFailed(it) }

        if (allowedAudiences.none { it in verified.audience.orEmpty() }) {
            log.warn(
                "[OIDC_AUDIENCE_MISMATCH] jwksUrl={} tokenAudience={} allowedAudiences={}",
                jwksUrl,
                verified.audience.orEmpty(),
                allowedAudiences,
            )
            throw clientFailed()
        }

        return verified.subject?.takeIf(String::isNotBlank) ?: throw clientFailed()
    }

    private suspend fun fetchPublicKey(keyId: String): RSAPublicKey {
        val response =
            runCatching { httpClient.get(jwksUrl) }
                .getOrElse { throw clientFailed(it) }

        if (!response.status.isSuccess()) {
            throw clientFailed()
        }

        val keys =
            runCatching { response.body<JsonWebKeySet>() }
                .getOrElse { throw clientFailed(it) }

        val jwk =
            keys.keys.firstOrNull { it.kid == keyId }
                ?: throw clientFailed()

        return runCatching { jwk.toRsaPublicKey() }
            .getOrElse { throw clientFailed(it) }
    }

    private fun clientFailed(cause: Throwable? = null): BusinessException =
        BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED, cause)

    private companion object {
        val log = LoggerFactory.getLogger(OidcIdTokenVerifier::class.java)
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
private data class JsonWebKeySet(
    val keys: List<JsonWebKey>,
)

@Serializable
private data class JsonWebKey(
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
